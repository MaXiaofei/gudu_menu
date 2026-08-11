package com.gudu.xsd.modules.menu;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gudu.xsd.common.BizException;
import com.gudu.xsd.modules.cookbook.CookingRecord;
import com.gudu.xsd.modules.cookbook.mapper.CookingRecordMapper;
import com.gudu.xsd.modules.dict.SysDict;
import com.gudu.xsd.modules.dict.mapper.DictMapper;
import com.gudu.xsd.modules.dish.Dish;
import com.gudu.xsd.modules.dish.DishIngredient;
import com.gudu.xsd.modules.dish.mapper.DishIngredientMapper;
import com.gudu.xsd.modules.dish.mapper.DishMapper;
import com.gudu.xsd.modules.menu.mapper.MenuDishMapper;
import com.gudu.xsd.modules.menu.mapper.MenuMapper;
import com.gudu.xsd.modules.menu.prep.MenuPrepStatus;
import com.gudu.xsd.modules.menu.prep.PrepStatus;
import com.gudu.xsd.modules.menu.prep.mapper.MenuPrepStatusMapper;
import com.gudu.xsd.modules.nutrition.Ingredient;
import com.gudu.xsd.modules.nutrition.mapper.IngredientMapper;
import com.gudu.xsd.modules.pantry.IngredientStock;
import com.gudu.xsd.modules.pantry.PantryService;
import com.gudu.xsd.modules.pantry.StockLog;
import com.gudu.xsd.modules.pantry.mapper.IngredientStockMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 做菜确认（V42 手动库存版）：不再自动扣库存，改为用户确认用材。
 *
 * <p>流程：点「开始做饭」→ GET cook-materials（本次用到的食材 + 当前档位 + 是否调料）
 * → 用户确认 usedUp/partiallyUsed → POST /menu/{id}/cook → 更新档位 + 写 cooking_record
 * （食记，与库存解耦）+ 食集标 DONE + 备菜全 READY。
 *
 * <p>完成态不再勾选采购清单（采购与食集状态完全解耦）。
 */
@Service
@RequiredArgsConstructor
public class CookService {

    static final String SOURCE_MENU = "menu";
    static final String MENU_STATUS_DONE = "DONE";

    private final MenuMapper menuMapper;
    private final MenuDishMapper menuDishMapper;
    private final DishIngredientMapper dishIngredientMapper;
    private final DishMapper dishMapper;
    private final CookingRecordMapper cookingRecordMapper;
    private final PantryService pantryService;
    private final NeedAggregator needAggregator;
    private final IngredientMapper ingredientMapper;
    private final IngredientStockMapper ingredientStockMapper;
    private final DictMapper dictMapper;
    private final MenuPrepStatusMapper menuPrepStatusMapper;

    /**
     * 做菜确认弹窗数据：本次用到的食材（用量原文）+ 当前档位 + 是否调料（调料默认"用了一些"）。
     * 只读聚合，不落库、不判断够不够。
     */
    public CookMaterialsVO cookMaterials(Long menuId) {
        Menu menu = menuMapper.selectById(menuId);
        if (menu == null) {
            throw new BizException("食集不存在");
        }
        Map<Long, List<NeedAggregator.UsageText>> needByIng = aggregateNeed(menuId);
        if (needByIng.isEmpty()) {
            return new CookMaterialsVO(menuId, List.of());
        }
        List<Long> ids = needByIng.keySet().stream().toList();
        Map<Long, Ingredient> ingMap = ingredientMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Ingredient::getId, Function.identity(), (a, b) -> a));
        Map<Long, String> levelMap = ingredientStockMapper.selectList(
                        new QueryWrapper<IngredientStock>().in("ingredient_id", ids)).stream()
                .collect(Collectors.toMap(IngredientStock::getIngredientId, IngredientStock::getLevel, (a, b) -> a));
        Set<Long> condimentCategoryIds = condimentCategoryIds();
        // dishId → 菜名（用量原文前缀）
        Map<Long, String> dishNameById = dishNameMap(needByIng.keySet().stream()
                .flatMap(k -> needByIng.get(k).stream()).map(NeedAggregator.UsageText::dishId)
                .filter(Objects::nonNull).distinct().toList());

        List<CookMaterialsVO.Item> items = new ArrayList<>();
        for (Long id : ids) {
            Ingredient ing = ingMap.get(id);
            if (ing == null) continue;
            List<String> usageTexts = needByIng.get(id).stream()
                    .map(u -> formatUsageText(dishNameById.get(u.dishId()), u))
                    .filter(Objects::nonNull)
                    .toList();
            items.add(new CookMaterialsVO.Item(
                    id,
                    ing.getName(),
                    usageTexts,
                    levelMap.getOrDefault(id, IngredientStock.LEVEL_NONE),
                    ing.getPurchaseCategoryId() != null && condimentCategoryIds.contains(ing.getPurchaseCategoryId())));
        }
        items.sort(java.util.Comparator
                .comparing((CookMaterialsVO.Item it) -> it.isCondiment()) // false=食材在前，true=调料在后
                .thenComparing(it -> it.ingredientName() == null ? "" : it.ingredientName(),
                        java.text.Collator.getInstance()));
        return new CookMaterialsVO(menuId, items);
    }

    /**
     * 整集做菜确认：按用户确认更新档位 → 每菜写 cooking_record → 食集标完成 → 备菜全 READY。
     *
     * @param usedUp        用户确认"用完了"的食材 id（→ NONE）
     * @param partiallyUsed 用户确认"用了一些"的食材 id（→ 降一档，LOW/NONE 不降）
     */
    @Transactional
    public CookResult cookByMenu(Long menuId, Long memberId, List<Long> usedUp, List<Long> partiallyUsed) {
        Menu menu = menuMapper.selectById(menuId);
        if (menu == null) {
            throw new BizException("食集不存在");
        }
        if (MENU_STATUS_DONE.equals(menu.getStatus())) {
            throw new BizException("这顿饭已经做完了");
        }
        if (usedUp != null) {
            for (Long id : usedUp) {
                if (id != null) pantryService.useUp(id, StockLog.ACTION_COOK, null);
            }
        }
        if (partiallyUsed != null) {
            for (Long id : partiallyUsed) {
                if (id != null) pantryService.partialUse(id, StockLog.ACTION_COOK_PARTIAL, null);
            }
        }

        Map<Long, List<NeedAggregator.UsageText>> needByIng = aggregateNeed(menuId);
        List<Long> dishIds = needByIng.isEmpty() ? List.of() : dishIds(menuId);
        // 用材总结（B6）：结构化写 memo（"用完:1,2;用了一些:3"），供食记页展示
        String memo = buildUsedMemo(usedUp, partiallyUsed);
        List<Long> recordIds = new ArrayList<>();
        for (Long dishId : dishIds) {
            BigDecimal dishFactor = dishFactor(menuId, dishId);
            recordIds.add(insertRecord(memberId, dishId, menuId, dishFactor, SOURCE_MENU, memo));
        }

        menu.setStatus(MENU_STATUS_DONE);
        menu.setFinishedAt(LocalDateTime.now());
        menuMapper.updateById(menu);

        markPrepReady(menuId, needByIng.keySet());
        return new CookResult(menuId, recordIds);
    }

    // ===================== 内部辅助 =====================

    /** 聚合食集各菜用量原文（by ingredientId）。 */
    private Map<Long, List<NeedAggregator.UsageText>> aggregateNeed(Long menuId) {
        List<MenuDish> mds = menuDishMapper.selectList(
                new QueryWrapper<MenuDish>().eq("menu_id", menuId));
        List<Long> dishIds = mds.stream().map(MenuDish::getDishId).filter(Objects::nonNull).distinct()
                .collect(Collectors.toList());
        Map<Long, List<DishIngredient>> byDish = loadDishIngredients(dishIds);
        fillUnitNames(byDish);
        return needAggregator.aggregate(mds, byDish);
    }

    /** 批量回填用量单位名（用量原文展示用：dish_ingredient.unit_id → sys_dict unit）。 */
    private void fillUnitNames(Map<Long, List<DishIngredient>> byDish) {
        List<Long> unitIds = byDish.values().stream().flatMap(List::stream)
                .map(DishIngredient::getUnitId).filter(Objects::nonNull).distinct().toList();
        if (unitIds.isEmpty()) return;
        Map<Long, String> unitNameById = dictMapper.selectBatchIds(unitIds).stream()
                .collect(Collectors.toMap(SysDict::getId, SysDict::getName, (a, b) -> a));
        byDish.values().stream().flatMap(List::stream)
                .forEach(di -> di.setUnitName(unitNameById.get(di.getUnitId())));
    }

    /** dishId → 菜名（用量原文前缀）。 */
    private Map<Long, String> dishNameMap(List<Long> dishIds) {
        if (dishIds.isEmpty()) return Map.of();
        return dishMapper.selectBatchIds(dishIds).stream()
                .collect(Collectors.toMap(Dish::getId, Dish::getName, (a, b) -> a));
    }

    /** 用量原文：「菜名 2个」；份数>1 时「菜名 2个 ×3」；用量缺失返回 null。 */
    private String formatUsageText(String dishName, NeedAggregator.UsageText u) {
        if (u.amount() == null) return null;
        String head = dishName != null ? dishName : ("菜#" + u.dishId());
        String amt = u.amount().stripTrailingZeros().toPlainString();
        StringBuilder sb = new StringBuilder(head).append(' ').append(amt);
        if (u.unitName() != null && !u.unitName().isBlank()) sb.append(u.unitName());
        if (u.servingFactor() != null && u.servingFactor().compareTo(BigDecimal.ONE) > 0) {
            sb.append(" ×").append(u.servingFactor().stripTrailingZeros().toPlainString());
        }
        return sb.toString();
    }

    private List<Long> dishIds(Long menuId) {
        return menuDishMapper.selectList(new QueryWrapper<MenuDish>().eq("menu_id", menuId))
                .stream().map(MenuDish::getDishId).filter(Objects::nonNull).distinct().toList();
    }

    private BigDecimal dishFactor(Long menuId, Long dishId) {
        return menuDishMapper.selectList(new QueryWrapper<MenuDish>().eq("menu_id", menuId))
                .stream()
                .filter(m -> dishId.equals(m.getDishId()))
                .map(MenuDish::getServingFactor).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Map<Long, List<DishIngredient>> loadDishIngredients(List<Long> dishIds) {
        if (dishIds.isEmpty()) return Map.of();
        List<DishIngredient> rows = dishIngredientMapper.selectList(
                new QueryWrapper<DishIngredient>().in("dish_id", dishIds));
        return rows.stream().collect(Collectors.groupingBy(DishIngredient::getDishId));
    }

    /** 调味料品类 id 集合（dict group=purchase_category，name=调味料）。弹窗调料默认"用了一些"。 */
    private Set<Long> condimentCategoryIds() {
        List<SysDict> rows = dictMapper.selectList(new QueryWrapper<SysDict>()
                .eq("dict_group", "purchase_category").eq("name", "调味料"));
        return rows.stream().map(SysDict::getId).collect(Collectors.toSet());
    }

    private Long insertRecord(Long memberId, Long dishId, Long menuId,
                              BigDecimal servingFactor, String source, String memo) {
        CookingRecord rec = new CookingRecord();
        rec.setMemberId(memberId);
        rec.setDishId(dishId);
        rec.setMenuId(menuId);
        rec.setServingFactor(servingFactor);
        rec.setSource(source);
        rec.setMemo(memo);
        rec.setCookedAt(LocalDateTime.now());
        cookingRecordMapper.insert(rec);
        return rec.getId();
    }

    /** 用材总结 memo："用完:1,2;用了一些:3"（ingredientId，分号分隔两段）。全空返回 null。 */
    private String buildUsedMemo(List<Long> usedUp, List<Long> partiallyUsed) {
        String used = usedUp == null ? "" : usedUp.stream().filter(Objects::nonNull)
                .map(String::valueOf).collect(Collectors.joining(","));
        String partial = partiallyUsed == null ? "" : partiallyUsed.stream().filter(Objects::nonNull)
                .map(String::valueOf).collect(Collectors.joining(","));
        if (used.isEmpty() && partial.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        if (!used.isEmpty()) sb.append("用完:").append(used);
        if (!partial.isEmpty()) {
            if (sb.length() > 0) sb.append(";");
            sb.append("用了一些:").append(partial);
        }
        return sb.toString();
    }

    /** 完成态：食集全部用料（主料+调料）备料状态置 READY（upsert，同 MenuPrepService.updateStatus）。 */
    private void markPrepReady(Long menuId, Set<Long> ingredientIds) {
        if (ingredientIds == null || ingredientIds.isEmpty()) return;
        for (Long ingId : ingredientIds) {
            MenuPrepStatus existing = menuPrepStatusMapper.selectOne(
                    new QueryWrapper<MenuPrepStatus>()
                            .eq("menu_id", menuId).eq("ingredient_id", ingId));
            if (existing == null) {
                MenuPrepStatus mps = new MenuPrepStatus();
                mps.setMenuId(menuId);
                mps.setIngredientId(ingId);
                mps.setStatus(PrepStatus.READY.name());
                menuPrepStatusMapper.insert(mps);
            } else {
                existing.setStatus(PrepStatus.READY.name());
                menuPrepStatusMapper.updateById(existing);
            }
        }
    }
}
