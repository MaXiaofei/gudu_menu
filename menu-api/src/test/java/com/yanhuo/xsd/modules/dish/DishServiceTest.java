package com.yanhuo.xsd.modules.dish;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.yanhuo.xsd.modules.dish.mapper.DishDictMapper;
import com.yanhuo.xsd.modules.dish.mapper.DishIngredientMapper;
import com.yanhuo.xsd.modules.dish.mapper.DishMapper;
import com.yanhuo.xsd.modules.dish.mapper.DishStepMapper;
import com.yanhuo.xsd.modules.nutrition.IngredientNutrition;
import com.yanhuo.xsd.modules.nutrition.NutritionCalcService;
import com.yanhuo.xsd.modules.nutrition.mapper.IngredientNutritionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 营养筛选单元测试：mock 全部 mapper，验证 search 营养过滤 + 手动分页正确。
 * 注：DishService 按「菜 1→(取食材→逐食材取营养)、菜 2→(...)」顺序调用 selectList，
 * mock 无法读 QueryWrapper 内 dishId，故按固定调用顺序用 thenReturn(a, b, c) 桩。
 */
class DishServiceTest {

    private DishMapper dishMapper;
    private DishStepMapper stepMapper;
    private DishDictMapper dictRelMapper;
    private DishIngredientMapper dishIngMapper;
    private IngredientNutritionMapper ingredientNutritionMapper;
    private DishService svc;

    @BeforeEach
    void setUp() {
        dishMapper = Mockito.mock(DishMapper.class);
        stepMapper = Mockito.mock(DishStepMapper.class);
        dictRelMapper = Mockito.mock(DishDictMapper.class);
        dishIngMapper = Mockito.mock(DishIngredientMapper.class);
        ingredientNutritionMapper = Mockito.mock(IngredientNutritionMapper.class);
        svc = new DishService(stepMapper, dictRelMapper, dishIngMapper, ingredientNutritionMapper, new NutritionCalcService());
        injectBaseMapper(svc, dishMapper);
    }

    private static void injectBaseMapper(DishService svc, DishMapper mapper) {
        try {
            var f = com.baomidou.mybatisplus.extension.service.impl.ServiceImpl.class.getDeclaredField("baseMapper");
            f.setAccessible(true);
            f.set(svc, mapper);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** 无营养约束：直接走 SQL 分页（selectList 不应被调用）。 */
    @Test
    void 无营养约束_走SQL分页_不触发list() {
        DishSearchDTO q = new DishSearchDTO();
        q.setPageNum(1);
        q.setPageSize(10);

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Dish> mp =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 10);
        mp.setRecords(List.of(dish(1L, "番茄炒蛋"), dish(2L, "黄瓜")));
        mp.setTotal(2);
        when(dishMapper.selectPage(any(), any(Wrapper.class))).thenReturn(mp);

        var page = svc.search(q);

        assertThat(page.getRecords()).extracting(Dish::getName)
                .containsExactly("番茄炒蛋", "黄瓜");
        Mockito.verify(dishIngMapper, Mockito.never()).selectList(any());
    }

    /** 有营养约束：超糖上限的菜被剔除。 */
    @Test
    void 有营养约束_超糖上限剔除() {
        DishSearchDTO q = new DishSearchDTO();
        q.setPageNum(1);
        q.setPageSize(10);
        Map<Long, BigDecimal> limits = new HashMap<>();
        limits.put(10L, new BigDecimal("25")); // metric 10 = 糖，上限 25g
        q.setNutritionLimits(limits);

        when(dishMapper.selectList(any())).thenReturn(
                List.of(dish(1L, "番茄炒蛋"), dish(2L, "拔丝地瓜")));

        // 调用顺序：菜1取食材 → 菜1 食材营养；菜2取食材 → 菜2 食材营养
        // dish_ingredient：菜1=[番茄100g]，菜2=[地瓜100g]
        when(dishIngMapper.selectList(any())).thenReturn(
                List.of(di(1, bd("100"))),   // 菜1
                List.of(di(2, bd("100"))));  // 菜2
        // 营养：番茄糖 per100g=20，地瓜糖 per100g=80
        when(ingredientNutritionMapper.selectList(any())).thenReturn(
                List.of(nut(10, bd("20"))),  // 番茄
                List.of(nut(10, bd("80")))); // 地瓜

        var page = svc.search(q);

        assertThat(page.getRecords()).extracting(Dish::getName).containsExactly("番茄炒蛋");
        assertThat(page.getTotal()).isEqualTo(1);
    }

    /** 多指标：任一超限即剔除。 */
    @Test
    void 多指标_任一超限剔除() {
        DishSearchDTO q = new DishSearchDTO();
        q.setPageNum(1);
        q.setPageSize(10);
        Map<Long, BigDecimal> limits = new HashMap<>();
        limits.put(10L, new BigDecimal("25")); // 糖 ≤25
        limits.put(20L, new BigDecimal("55")); // GI ≤55
        q.setNutritionLimits(limits);

        when(dishMapper.selectList(any())).thenReturn(
                List.of(dish(1L, "清炒青菜"), dish(2L, "白米饭")));

        // 菜1青菜：100g，糖5/GI40 → 通过；菜2米饭：100g，糖0/GI83 → GI 超限
        // dish_ingredient 顺序：菜1(1食材)、菜2(1食材)
        when(dishIngMapper.selectList(any())).thenReturn(
                List.of(di(1, bd("100"))),
                List.of(di(2, bd("100"))));
        // 营养：青菜取1次返回2指标；米饭取1次返回2指标
        when(ingredientNutritionMapper.selectList(any())).thenReturn(
                List.of(nut(10, bd("5")), nut(20, bd("40"))),   // 青菜
                List.of(nut(10, bd("0")), nut(20, bd("83"))));  // 米饭

        var page = svc.search(q);

        assertThat(page.getRecords()).extracting(Dish::getName).containsExactly("清炒青菜");
    }

    /** 分页：候选 5 条全过，pageSize=2 → 第二页 2 条，total=5。 */
    @Test
    void 营养筛选_分页正确() {
        DishSearchDTO q = new DishSearchDTO();
        q.setPageNum(2);
        q.setPageSize(2);
        Map<Long, BigDecimal> limits = new HashMap<>();
        limits.put(10L, new BigDecimal("100"));
        q.setNutritionLimits(limits);

        List<Dish> all = new ArrayList<>();
        for (long i = 1; i <= 5; i++) all.add(dish(i, "菜" + i));
        when(dishMapper.selectList(any())).thenReturn(all);

        // 每菜1食材(10g)糖 per100g=1 → 糖 0.1g，全通过
        @SuppressWarnings("unchecked")
        List<List<DishIngredient>> disLists = new ArrayList<>();
        for (long i = 1; i <= 5; i++) disLists.add(List.of(di(i, bd("10"))));
        when(dishIngMapper.selectList(any())).thenReturn(
                disLists.get(0), disLists.get(1), disLists.get(2), disLists.get(3), disLists.get(4));
        @SuppressWarnings("unchecked")
        List<List<IngredientNutrition>> nutLists = new ArrayList<>();
        for (long i = 1; i <= 5; i++) nutLists.add(List.of(nut(10, bd("1"))));
        when(ingredientNutritionMapper.selectList(any())).thenReturn(
                nutLists.get(0), nutLists.get(1), nutLists.get(2), nutLists.get(3), nutLists.get(4));

        var page = svc.search(q);

        assertThat(page.getTotal()).isEqualTo(5);
        assertThat(page.getRecords()).extracting(Dish::getName).containsExactly("菜3", "菜4");
    }

    private static BigDecimal bd(String s) { return new BigDecimal(s); }

    private Dish dish(long id, String name) {
        Dish d = new Dish();
        d.setId(id);
        d.setName(name);
        return d;
    }

    private static DishIngredient di(long ingId, BigDecimal grams) {
        DishIngredient di = new DishIngredient();
        di.setIngredientId(ingId);
        di.setAmount(grams);
        return di;
    }

    private static IngredientNutrition nut(long metricId, BigDecimal value) {
        IngredientNutrition n = new IngredientNutrition();
        n.setMetricId(metricId);
        n.setValue(value);
        return n;
    }
}
