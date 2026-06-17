package com.yanhuo.xsd.modules.menu;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yanhuo.xsd.modules.dish.Dish;
import com.yanhuo.xsd.modules.dish.DishQueryService;
import com.yanhuo.xsd.modules.dish.mapper.DishMapper;
import com.yanhuo.xsd.modules.menu.mapper.MenuDishMapper;
import com.yanhuo.xsd.modules.menu.mapper.MenuMapper;
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
            BigDecimal price = (dish != null && dish.getPrice() != null) ? dish.getPrice() : BigDecimal.ZERO;
            Map<Long, BigDecimal> nut = dishQueryService.nutrition(d.getDishId(), BigDecimal.ONE);
            BigDecimal factor = (d.getServingFactor() != null) ? d.getServingFactor() : BigDecimal.ONE;
            lines.add(new MenuCalcService.MenuLine(price, nut, factor));
        }
        return new MenuSummary(menuCalc.totalPrice(lines), menuCalc.totalNutrition(lines));
    }

    public record MenuDetail(Menu menu, List<MenuDish> dishes) {}

    public record MenuSummary(BigDecimal totalPrice, Map<Long, BigDecimal> totalNutrition) {}
}
