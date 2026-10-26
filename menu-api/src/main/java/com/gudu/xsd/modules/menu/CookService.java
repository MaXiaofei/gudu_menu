package com.gudu.xsd.modules.menu;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gudu.xsd.common.BizException;
import com.gudu.xsd.common.domain.DomainEventPublisher;
import com.gudu.xsd.modules.cookbook.application.CookingRecordAppService;
import com.gudu.xsd.modules.dict.DictService;
import com.gudu.xsd.modules.dict.SysDict;
import com.gudu.xsd.modules.dish.Dish;
import com.gudu.xsd.modules.dish.DishIngredient;
import com.gudu.xsd.modules.dish.DishService;
import com.gudu.xsd.modules.menu.mapper.MenuDishMapper;
import com.gudu.xsd.modules.menu.mapper.MenuMapper;
import com.gudu.xsd.modules.menu.prep.MenuPrepStatus;
import com.gudu.xsd.modules.menu.prep.PrepStatus;
import com.gudu.xsd.modules.menu.prep.mapper.MenuPrepStatusMapper;
import com.gudu.xsd.modules.menu.domain.event.DishCookedEvent;
import com.gudu.xsd.modules.menu.domain.model.CookSession;
import com.gudu.xsd.modules.menu.domain.model.CookedDish;
import com.gudu.xsd.modules.nutrition.Ingredient;
import com.gudu.xsd.modules.nutrition.IngredientService;
import com.gudu.xsd.modules.pantry.application.StockAppService;
import com.gudu.xsd.modules.pantry.domain.model.StockLevel;
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
 *
 * <p>feature-gaps-closure（DDD 绞杀）：不再直连 pantry/cookbook——cookByMenu 经 {@link CookSession}
 * 发布 {@link DishCookedEvent}，由 pantry（回写档位）与 cookbook（写 cooking_record）各自订阅；
 * 跨模块读取一律走对方 Service（DishService / IngredientService / DictService / StockAppService）。
 */
@Service
@RequiredArgsConstructor
public class CookService {

    static final String MENU_STATUS_DONE = "DONE";

    private final MenuMapper menuMapper;
    private final MenuDishMapper menuDishMapper;
    private final NeedAggregator needAggregator;
    private final MenuPrepStatusMapper menuPrepStatusMapper;
    private final DishService dishService;
    private final IngredientService ingredientService;
    private final DictService dictService;
    private final StockAppService stockAppService;
    private final CookingRecordAppService cookingRecordAppService;
    private final DomainEventPublisher eventPublisher;

    /**
     * 做菜确认弹窗数据：本次用到的食材（用量原文）+ 当前档位 + 是否调料（调料默认"用了一些"）。
     * 只读聚合，不落库、不判断够不够。
     */
    public CookMaterialsVO cookMaterials(Long menuId) {
        Menu menu = menuMapper.selectById(menuId);
        if (menu == null) {
            throw new BizException("食集不存在");
        }
        return materials(menuId, menuDishMapper.selectList(new QueryWrapper<MenuDish>().eq("menu_id", menuId)));
    }

    /**
     * 单菜直做确认弹窗数据（GET /cook/dish/{dishId}/materials）：结构同食集版，menuId=null；用量原文按份数缩放。
     */
    public CookMaterialsVO dishMaterials(Long dishId, java.math.BigDecimal servings) {
        if (dishId == null || dishService.getById(dishId) == null) {
            throw BizException.notFound("菜谱不存在");
        }
        MenuDish one = new MenuDish();
        one.setDishId(dishId);
        one.setServingFactor(servings == null || servings.signum() <= 0 ? BigDecimal.ONE : servings);
        return materials(null, List.of(one));
    }

    /** 用到的食材 + 用量原文 + 当前档位 + 是否调料（只读聚合，跨模块读取全部批量）。 */
    private CookMaterialsVO materials(Long menuId, List<MenuDish> mds) {
        Map<Long, List<NeedAggregator.UsageText>> needByIng = aggregateNeed(mds);
        if (needByIng.isEmpty()) {
            return new CookMaterialsVO(menuId, List.of());
        }
        List<Long> ids = needByIng.keySet().stream().toList();
        Map<Long, Ingredient> ingMap = ingredientService.listByIds(ids).stream()
                .collect(Collectors.toMap(Ingredient::getId, Function.identity(), (a, b) -> a));
        Map<Long, StockLevel> levelMap = stockAppService.levelsOf(ids);
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
                    .map(u -> UsageTextFormatter.format(u.dishId(), dishNameById.get(u.dishId()),
                            u.amount(), u.unitName(), u.servingFactor()))
                    .filter(Objects::nonNull)
                    .toList();
            items.add(new CookMaterialsVO.Item(
                    id,
                    ing.getName(),
                    usageTexts,
                    levelMap.getOrDefault(id, StockLevel.NONE).name(),
                    ing.getPurchaseCategoryId() != null && condimentCategoryIds.contains(ing.getPurchaseCategoryId()),
                    ing.getIconKey()));
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
    @Transactional(rollbackFor = Exception.class)
    public CookResult cookByMenu(Long menuId, Long memberId, List<Long> usedUp, List<Long> partiallyUsed) {
        Menu menu = menuMapper.selectById(menuId);
        if (menu == null) {
            throw new BizException("食集不存在");
        }
        if (MENU_STATUS_DONE.equals(menu.getStatus())) {
            throw new BizException("这顿饭已经做完了");
        }
        List<MenuDish> mds = menuDishMapper.selectList(new QueryWrapper<MenuDish>().eq("menu_id", menuId));
        Map<Long, List<NeedAggregator.UsageText>> needByIng = aggregateNeed(mds);
        List<Long> dishIds = needByIng.isEmpty() ? List.of()
                : mds.stream().map(MenuDish::getDishId).filter(Objects::nonNull).distinct().toList();
        List<CookedDish> dishes = dishIds.stream().map(id -> new CookedDish(id, dishFactor(mds, id))).toList();

        // 领域事件：pantry 回写档位 + cookbook 写食记（同步、本事务内）
        CookSession session = CookSession.forMenu(memberId, menuId, dishes, usedUp, partiallyUsed);
        DishCookedEvent event = session.complete();
        eventPublisher.publishFrom(session);
        List<Long> recordIds = cookingRecordAppService.recordIdsOf(event.eventId());

        menu.setStatus(MENU_STATUS_DONE);
        menu.setFinishedAt(LocalDateTime.now());
        menuMapper.updateById(menu);

        markPrepReady(menuId, needByIng.keySet());
        return new CookResult(menuId, recordIds);
    }

    // ===================== 内部辅助 =====================

    /** 聚合各菜用量原文（by ingredientId）。 */
    private Map<Long, List<NeedAggregator.UsageText>> aggregateNeed(List<MenuDish> mds) {
        List<Long> dishIds = mds.stream().map(MenuDish::getDishId).filter(Objects::nonNull).distinct()
                .collect(Collectors.toList());
        Map<Long, List<DishIngredient>> byDish = dishIds.isEmpty() ? Map.of()
                : dishService.ingredientsOf(dishIds).stream().collect(Collectors.groupingBy(DishIngredient::getDishId));
        fillUnitNames(byDish);
        return needAggregator.aggregate(mds, byDish);
    }

    /** 批量回填用量单位名（用量原文展示用：dish_ingredient.unit_id → sys_dict unit）。 */
    private void fillUnitNames(Map<Long, List<DishIngredient>> byDish) {
        List<Long> unitIds = byDish.values().stream().flatMap(List::stream)
                .map(DishIngredient::getUnitId).filter(Objects::nonNull).distinct().toList();
        if (unitIds.isEmpty()) return;
        Map<Long, String> unitNameById = dictService.listByIds(unitIds).stream()
                .collect(Collectors.toMap(SysDict::getId, SysDict::getName, (a, b) -> a));
        byDish.values().stream().flatMap(List::stream)
                .forEach(di -> di.setUnitName(unitNameById.get(di.getUnitId())));
    }

    /** dishId → 菜名（用量原文前缀）。 */
    private Map<Long, String> dishNameMap(List<Long> dishIds) {
        if (dishIds.isEmpty()) return Map.of();
        return dishService.listByIds(dishIds).stream()
                .collect(Collectors.toMap(Dish::getId, Dish::getName, (a, b) -> a));
    }

    /** 用量原文格式化抽到 {@link UsageTextFormatter}（V55 共享，做菜确认/备菜同用）。 */

    private static BigDecimal dishFactor(List<MenuDish> mds, Long dishId) {
        return mds.stream()
                .filter(m -> dishId.equals(m.getDishId()))
                .map(MenuDish::getServingFactor).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** 调味料品类 id 集合（dict group=purchase_category，name=调味料）。弹窗调料默认"用了一些"。 */
    private Set<Long> condimentCategoryIds() {
        return dictService.idsByName("purchase_category", "调味料");
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
