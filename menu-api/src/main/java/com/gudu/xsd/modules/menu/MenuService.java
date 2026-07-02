package com.gudu.xsd.modules.menu;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gudu.xsd.common.PageQuery;
import com.gudu.xsd.modules.dish.Dish;
import com.gudu.xsd.modules.dish.DishIngredient;
import com.gudu.xsd.modules.dish.DishQueryService;
import com.gudu.xsd.modules.dish.mapper.DishIngredientMapper;
import com.gudu.xsd.modules.dish.mapper.DishMapper;
import com.gudu.xsd.modules.nutrition.Ingredient;
import com.gudu.xsd.modules.nutrition.UnitConvertService;
import com.gudu.xsd.modules.nutrition.mapper.IngredientMapper;
import com.gudu.xsd.modules.menu.mapper.MenuDishMapper;
import com.gudu.xsd.modules.menu.mapper.MenuMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MenuService extends ServiceImpl<MenuMapper, Menu> {

    private final MenuDishMapper menuDishMapper;
    private final DishMapper dishMapper;
    private final DishQueryService dishQueryService;
    private final MenuCalcService menuCalc;
    private final DishIngredientMapper dishIngredientMapper;
    private final IngredientMapper ingredientMapper;
    private final UnitConvertService unitConvert;

    /** 分页查（后台管理）。按创建时间倒序。 */
    public IPage<Menu> page(PageQuery q) {
        return page(new Page<>(q.getPageNum(), q.getPageSize()),
                new QueryWrapper<Menu>().orderByDesc("create_time"));
    }

    /** 保存菜单并整体替换其菜品关联。 */
    @Transactional
    public void saveWithDishes(MenuSaveDTO dto) {
        Menu menu = dto.getMenu();
        if (menu.getServingCount() == null) {
            menu.setServingCount(1);
        }
        saveOrUpdate(menu);
        menuDishMapper.delete(new QueryWrapper<MenuDish>().eq("menu_id", menu.getId()));
        if (dto.getDishes() != null) {
            for (MenuDish md : dto.getDishes()) {
                md.setId(null);
                md.setMenuId(menu.getId());
                menuDishMapper.insert(md);
            }
        }
    }

    /** 详情：菜单 + 关联菜品列表。 */
    public MenuDetail detail(Long id) {
        Menu menu = getById(id);
        List<MenuDish> dishes = menuDishMapper.selectList(
                new QueryWrapper<MenuDish>().eq("menu_id", id).orderByAsc("id"));
        return new MenuDetail(menu, dishes);
    }

    /** 菜单汇总：各菜份数营养（复用 NutritionCalcService）+ 价格，调 MenuCalcService 纯函数。 */
    public MenuSummary summary(Long id) {
        MenuDetail md = detail(id);
        List<MenuCalcService.MenuLine> lines = new ArrayList<>();
        for (MenuDish d : md.dishes()) {
            Dish dish = dishMapper.selectById(d.getDishId());
            // 价格改按食材用量算（评审§12）：Σ(dish_ingredient.grams × 每克单价)
            BigDecimal price = (dish != null) ? priceByIngredients(dish.getId()) : BigDecimal.ZERO;
            Map<Long, BigDecimal> nut = dishQueryService.nutrition(d.getDishId(), BigDecimal.ONE);
            BigDecimal factor = (d.getServingFactor() != null) ? d.getServingFactor() : BigDecimal.ONE;
            lines.add(new MenuCalcService.MenuLine(price, nut, factor));
        }
        return new MenuSummary(menuCalc.totalPrice(lines), menuCalc.totalNutrition(lines));
    }

    /** 按用量算单菜 1 份价格 = Σ(dish_ingredient.grams × 每克单价)。
     *  每克单价 = ingredient.price / defaultGramsPerUnit；未配置换算的食材跳过（评审§12）。 */
    private BigDecimal priceByIngredients(Long dishId) {
        List<DishIngredient> dis = dishIngredientMapper.selectList(
                new QueryWrapper<DishIngredient>().eq("dish_id", dishId));
        if (dis.isEmpty()) return BigDecimal.ZERO;
        List<Long> ingIds = dis.stream().map(DishIngredient::getIngredientId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        if (ingIds.isEmpty()) return BigDecimal.ZERO;
        Map<Long, Ingredient> ingById = ingredientMapper.selectBatchIds(ingIds).stream()
                .collect(java.util.stream.Collectors.toMap(Ingredient::getId, i -> i, (a, b) -> a));
        BigDecimal sum = BigDecimal.ZERO;
        for (DishIngredient di : dis) {
            Ingredient ing = ingById.get(di.getIngredientId());
            if (ing == null || ing.getPrice() == null) continue;
            BigDecimal gpu = unitConvert.defaultGramsPerUnit(di.getIngredientId());
            if (gpu == null || gpu.signum() == 0) continue;  // 未配置换算，跳过
            BigDecimal grams = di.getGrams() != null ? di.getGrams() : di.getAmount();
            if (grams == null) continue;
            sum = sum.add(ing.getPrice().divide(gpu, 6, java.math.RoundingMode.HALF_UP).multiply(grams));
        }
        return sum;
    }

    public record MenuDetail(Menu menu, List<MenuDish> dishes) {}

    public record MenuSummary(BigDecimal totalPrice, Map<Long, BigDecimal> totalNutrition) {}
}
