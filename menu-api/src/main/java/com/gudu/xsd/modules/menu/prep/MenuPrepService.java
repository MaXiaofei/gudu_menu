package com.gudu.xsd.modules.menu.prep;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gudu.xsd.modules.dict.SysDict;
import com.gudu.xsd.modules.dict.mapper.DictMapper;
import com.gudu.xsd.modules.dish.DishIngredient;
import com.gudu.xsd.modules.dish.mapper.DishIngredientMapper;
import com.gudu.xsd.modules.menu.MenuService;
import com.gudu.xsd.modules.menu.UsageTextFormatter;
import com.gudu.xsd.modules.nutrition.Ingredient;
import com.gudu.xsd.modules.nutrition.mapper.IngredientMapper;
import com.gudu.xsd.modules.menu.prep.mapper.MenuPrepStatusMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 备菜服务（备菜模块 Plan C）。
 *
 * <p>{@link #getPrep(Long)} 聚合食集各菜用料（×servingFactor，不减库存）→ 主料/调料分组 +
 * 备料状态关联 + 共用高亮 + 进度计数。{@link #updateStatus} upsert 备料状态。
 *
 * <p>主料进 items；调料（purchaseCategoryId=调味料品类）折叠到 condiments。两者都计入
 * readyCount/totalCount（调料也是备料动作，可勾选、算进度）。
 */
@Service
@RequiredArgsConstructor
public class MenuPrepService {

    /** 调味料品类 id（shopping 已用，盐/油/豉油 折叠到 condiments 组）。 */
    private static final long CONDIMENT_CATEGORY_ID = 30L;

    private final MenuService menuService;
    private final DishIngredientMapper dishIngredientMapper;
    private final IngredientMapper ingredientMapper;
    private final MenuPrepStatusMapper menuPrepStatusMapper;
    private final PrepAggregator aggregator;
    private final com.gudu.xsd.modules.pantry.PantryService pantryService;
    private final DictMapper dictMapper;

    /** GET /menu/{id}/prep：聚合全量用料 + 备料状态 + 共用高亮 + 调料分组 + 进度（含调料）。 */
    public MenuPrepVO getPrep(Long menuId) {
        MenuService.MenuDetail md = menuService.detail(menuId);
        List<MenuService.MenuDishVO> dishes = md.dishes();
        if (dishes.isEmpty()) return new MenuPrepVO(List.of(), List.of(), 0, 0);

        // 1. 批量查各菜 DishIngredient（一次 in 查询）
        List<Long> dishIds = dishes.stream().map(MenuService.MenuDishVO::dishId).toList();
        List<DishIngredient> dis = dishIngredientMapper.selectList(
                new QueryWrapper<DishIngredient>().in("dish_id", dishIds));

        // 2. 批量回填单位名（用量原文展示用：dish_ingredient.unit_id → sys_dict unit）
        Map<Long, String> unitNameById = unitNameMap(dis);
        // 3. 构造 Usage（每菜的 servingFactor + 用量原文 amount/unitName；V55 不再算克）
        Map<Long, BigDecimal> factorByDish = dishes.stream().collect(Collectors.toMap(
                MenuService.MenuDishVO::dishId,
                d -> d.servingFactor() != null ? d.servingFactor() : BigDecimal.ONE,
                (a, b) -> a));
        List<PrepAggregator.Usage> usages = dis.stream()
                .filter(di -> di.getIngredientId() != null && di.getDishId() != null)
                .map(di -> new PrepAggregator.Usage(
                        di.getDishId(),
                        factorByDish.getOrDefault(di.getDishId(), BigDecimal.ONE),
                        di.getIngredientId(),
                        di.getAmount(),
                        unitNameById.get(di.getUnitId())))
                .toList();

        // 4. 聚合（纯函数，已单测）
        List<PrepAggregator.PrepLine> lines = aggregator.aggregate(usages);

        // 5. 批量查 ingredient（name + purchaseCategoryId 判调料）
        List<Long> ingIds = lines.stream().map(PrepAggregator.PrepLine::ingredientId).toList();
        Map<Long, Ingredient> ingById = ingIds.isEmpty() ? Map.of()
                : ingredientMapper.selectBatchIds(ingIds).stream()
                        .collect(Collectors.toMap(Ingredient::getId, i -> i, (a, b) -> a));

        // 5. 一次查该食集所有备料状态
        List<MenuPrepStatus> statuses = menuPrepStatusMapper.selectList(
                new QueryWrapper<MenuPrepStatus>().eq("menu_id", menuId));
        Map<Long, String> statusByIng = statuses.stream()
                .collect(Collectors.toMap(MenuPrepStatus::getIngredientId, MenuPrepStatus::getStatus, (a, b) -> a));

        // 6. dishId → dishName（复用 #2 detail 冗余的 dishName）
        Map<Long, String> dishNameById = dishes.stream().collect(Collectors.toMap(
                MenuService.MenuDishVO::dishId,
                d -> d.dishName() != null ? d.dishName() : ("菜#" + d.dishId()),
                (a, b) -> a));

        // 6.5 批量读库存档位（备菜徽标：家里 充足/不足/用完）
        Map<Long, String> levelByIng = pantryService.levelMap(ingIds);

        // 7. 组装：主料/调料分组 + 进度（两者都计入 totalCount/readyCount）
        List<PrepItemVO> items = new ArrayList<>();
        List<PrepItemVO> condiments = new ArrayList<>();
        int readyCount = 0;
        for (PrepAggregator.PrepLine line : lines) {
            Ingredient ing = ingById.get(line.ingredientId());
            String name = ing != null && ing.getName() != null ? ing.getName() : ("食材#" + line.ingredientId());
            boolean isCondiment = ing != null && ing.getPurchaseCategoryId() != null
                    && ing.getPurchaseCategoryId() == CONDIMENT_CATEGORY_ID;
            List<String> dishNames = line.dishIds().stream()
                    .map(dishNameById::get).filter(Objects::nonNull).toList();
            // V55：用量原文（如「番茄炒蛋 2个」；份数>1 时「2个 ×3」）
            List<String> usageTexts = line.usages().stream()
                    .map(u -> UsageTextFormatter.format(u.dishId(), dishNameById.get(u.dishId()),
                            u.amount(), u.unitName(), u.servingFactor()))
                    .filter(Objects::nonNull)
                    .toList();
            String status = statusByIng.getOrDefault(line.ingredientId(), PrepStatus.PENDING.name());
            String stockLevel = levelByIng.getOrDefault(line.ingredientId(), "NONE");
            PrepItemVO vo = new PrepItemVO(line.ingredientId(), name, usageTexts,
                    line.dishCount(), dishNames, status, line.dishCount() >= 2, stockLevel);
            if (isCondiment) {
                condiments.add(vo);
            } else {
                items.add(vo);
            }
            if (PrepStatus.READY.name().equals(status)) readyCount++;
        }
        return new MenuPrepVO(items, condiments, readyCount, items.size() + condiments.size());
    }

    /** 单位字典（sys_dict group=unit）id -> name；unitId 缺失时返回 null（HashMap 容忍 null key）。 */
    private Map<Long, String> unitNameMap(List<DishIngredient> dis) {
        List<Long> unitIds = dis.stream().map(DishIngredient::getUnitId)
                .filter(Objects::nonNull).distinct().toList();
        if (unitIds.isEmpty()) return new HashMap<>();
        return dictMapper.selectBatchIds(unitIds).stream()
                .collect(Collectors.toMap(SysDict::getId, SysDict::getName, (a, b) -> a));
    }

    /** 用量原文格式化抽到 {@link UsageTextFormatter}（V55 共享，备菜/做菜确认同用）。 */

    /** PUT /menu/{id}/prep/{ingredientId}：upsert 备料状态（无则 insert，有则 update）。 */
    @Transactional
    public void updateStatus(Long menuId, Long ingredientId, PrepStatus status) {
        MenuPrepStatus existing = menuPrepStatusMapper.selectOne(
                new QueryWrapper<MenuPrepStatus>()
                        .eq("menu_id", menuId).eq("ingredient_id", ingredientId));
        if (existing == null) {
            MenuPrepStatus mps = new MenuPrepStatus();
            mps.setMenuId(menuId);
            mps.setIngredientId(ingredientId);
            mps.setStatus(status.name());
            menuPrepStatusMapper.insert(mps);
        } else {
            existing.setStatus(status.name());
            menuPrepStatusMapper.updateById(existing);
        }
    }
}
