package com.gudu.xsd.modules.menu;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gudu.xsd.common.BizException;
import com.gudu.xsd.modules.cookbook.CookingRecord;
import com.gudu.xsd.modules.cookbook.mapper.CookingRecordMapper;
import com.gudu.xsd.modules.dish.DishIngredient;
import com.gudu.xsd.modules.dish.mapper.DishIngredientMapper;
import com.gudu.xsd.modules.menu.mapper.MenuDishMapper;
import com.gudu.xsd.modules.menu.mapper.MenuMapper;
import com.gudu.xsd.modules.nutrition.Ingredient;
import com.gudu.xsd.modules.nutrition.mapper.IngredientMapper;
import com.gudu.xsd.modules.pantry.PantryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 做菜扣库存编排：聚合用量 → 按 ingredientId FIFO 扣 pantry → 每菜写一条 cooking_record
 * （带 menuId/servingFactor/source/memo）→ 整集做把 menu 标 DONE。
 *
 * 扣减规则（铁律）：扣到 0 为止、pantry 不记负，扣不动的欠量写 cooking_record.memo。
 */
@Service
@RequiredArgsConstructor
public class CookService {

    static final String SOURCE_MENU = "menu";
    static final String SOURCE_DISH = "dish";
    static final String MENU_STATUS_DONE = "DONE";

    private final MenuMapper menuMapper;
    private final MenuDishMapper menuDishMapper;
    private final DishIngredientMapper dishIngredientMapper;
    private final CookingRecordMapper cookingRecordMapper;
    private final PantryService pantryService;
    private final NeedAggregator needAggregator;
    private final IngredientMapper ingredientMapper;

    /** 整集做：聚合食集各菜用量 → 扣减 → 每菜写 record → menu 标完成。 */
    @Transactional
    public CookResult cookByMenu(Long menuId, Long memberId) {
        Menu menu = menuMapper.selectById(menuId);
        if (menu == null) {
            throw new BizException("食集不存在");
        }
        List<MenuDish> mds = menuDishMapper.selectList(
                new QueryWrapper<MenuDish>().eq("menu_id", menuId));
        List<Long> dishIds = mds.stream().map(MenuDish::getDishId).filter(Objects::nonNull).distinct()
                .collect(Collectors.toList());
        Map<Long, List<DishIngredient>> byDish = loadDishIngredients(dishIds);
        Map<Long, BigDecimal> needByIng = needAggregator.aggregate(mds, byDish);

        Map<Long, BigDecimal> shortages = new LinkedHashMap<>();
        List<PantryService.DeductResult> deductions = deductAll(needByIng, shortages);

        // 每菜写一条 cooking_record（都带 menuId，source=menu，memo=全量欠量）
        String memo = buildShortageMemo(shortages);
        List<Long> recordIds = new ArrayList<>();
        for (Long dishId : dishIds) {
            BigDecimal dishFactor = mds.stream()
                    .filter(m -> dishId.equals(m.getDishId()))
                    .map(MenuDish::getServingFactor).filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            recordIds.add(insertRecord(memberId, dishId, menuId, dishFactor, SOURCE_MENU, memo));
        }

        menu.setStatus(MENU_STATUS_DONE);
        menu.setFinishedAt(LocalDateTime.now());
        menuMapper.updateById(menu);

        return new CookResult(menuId, deductions, shortages, recordIds);
    }

    /** 单菜直做：不入食集，source=dish。 */
    @Transactional
    public CookResult cookByDish(Long dishId, BigDecimal servings, Long memberId) {
        BigDecimal factor = servings == null || servings.signum() <= 0 ? BigDecimal.ONE : servings;
        Map<Long, List<DishIngredient>> byDish = loadDishIngredients(List.of(dishId));
        Map<Long, BigDecimal> needByIng = new HashMap<>();
        List<DishIngredient> ings = byDish.getOrDefault(dishId, List.of());
        for (DishIngredient di : ings) {
            if (di == null || di.getIngredientId() == null || di.getGrams() == null) continue;
            needByIng.merge(di.getIngredientId(), di.getGrams().multiply(factor), BigDecimal::add);
        }

        Map<Long, BigDecimal> shortages = new LinkedHashMap<>();
        List<PantryService.DeductResult> deductions = deductAll(needByIng, shortages);

        Long recId = insertRecord(memberId, dishId, null, factor, SOURCE_DISH, buildShortageMemo(shortages));
        return new CookResult(null, deductions, shortages, List.of(recId));
    }

    // ===================== 内部辅助 =====================

    private Map<Long, List<DishIngredient>> loadDishIngredients(List<Long> dishIds) {
        if (dishIds.isEmpty()) return Map.of();
        List<DishIngredient> rows = dishIngredientMapper.selectList(
                new QueryWrapper<DishIngredient>().in("dish_id", dishIds));
        return rows.stream().collect(Collectors.groupingBy(DishIngredient::getDishId));
    }

    private List<PantryService.DeductResult> deductAll(Map<Long, BigDecimal> needByIng,
                                                       Map<Long, BigDecimal> shortages) {
        List<PantryService.DeductResult> out = new ArrayList<>();
        for (Map.Entry<Long, BigDecimal> e : needByIng.entrySet()) {
            PantryService.DeductResult r = pantryService.deductByIngredient(e.getKey(), e.getValue());
            out.add(r);
            if (r.shortageGrams() != null && r.shortageGrams().signum() > 0) {
                shortages.put(e.getKey(), r.shortageGrams());
            }
        }
        fillIngredientNames(out);
        return out;
    }

    /** 批量回填扣减明细的食材名（一次查 ingredient 表，避免前端拿 id 反查）。 */
    private void fillIngredientNames(List<PantryService.DeductResult> deductions) {
        if (deductions.isEmpty()) return;
        List<Long> ids = deductions.stream()
                .map(PantryService.DeductResult::ingredientId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (ids.isEmpty()) return;
        List<Ingredient> rows = ingredientMapper.selectBatchIds(ids);
        if (rows == null || rows.isEmpty()) return;
        Map<Long, String> nameMap = rows.stream()
                .collect(Collectors.toMap(Ingredient::getId, Ingredient::getName, (a, b) -> a));
        for (int i = 0; i < deductions.size(); i++) {
            PantryService.DeductResult r = deductions.get(i);
            String name = r.ingredientId() == null ? null : nameMap.get(r.ingredientId());
            deductions.set(i, new PantryService.DeductResult(
                    r.ingredientId(), name, r.deductedGrams(), r.shortageGrams(), r.batches()));
        }
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

    /** 欠量 memo："10:70g;20:50g"（ingredientId:克g）。前端翻译食材名。 */
    private String buildShortageMemo(Map<Long, BigDecimal> shortages) {
        if (shortages.isEmpty()) return null;
        return shortages.entrySet().stream()
                .map(e -> e.getKey() + ":" + e.getValue().setScale(0, RoundingMode.HALF_UP) + "g")
                .collect(Collectors.joining(";"));
    }
}
