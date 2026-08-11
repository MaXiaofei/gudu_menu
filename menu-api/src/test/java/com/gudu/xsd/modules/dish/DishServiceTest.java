package com.gudu.xsd.modules.dish;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.gudu.xsd.modules.cookbook.mapper.CookingRecordMapper;
import com.gudu.xsd.modules.dish.mapper.DishDictMapper;
import com.gudu.xsd.modules.dish.mapper.DishHistoryMapper;
import com.gudu.xsd.modules.dish.mapper.DishIngredientMapper;
import com.gudu.xsd.modules.dish.mapper.DishMapper;
import com.gudu.xsd.modules.dish.mapper.DishStepMapper;
import com.gudu.xsd.modules.nutrition.Ingredient;
import com.gudu.xsd.modules.nutrition.IngredientNutrition;
import com.gudu.xsd.modules.nutrition.NutritionCalcService;
import com.gudu.xsd.modules.dict.SysDict;
import com.gudu.xsd.modules.dict.mapper.DictMapper;
import com.gudu.xsd.modules.nutrition.mapper.IngredientMapper;
import com.gudu.xsd.modules.nutrition.mapper.IngredientNutritionMapper;
import com.gudu.xsd.modules.pantry.PantryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
    private DishHistoryMapper historyMapper;
    private IngredientNutritionMapper ingredientNutritionMapper;
    private IngredientMapper ingredientMapper;
    private CookingRecordMapper cookingRecordMapper;
    private DictMapper dictMapper;
    private PantryService pantryService;
    private DishService svc;

    @BeforeEach
    void setUp() {
        dishMapper = Mockito.mock(DishMapper.class);
        stepMapper = Mockito.mock(DishStepMapper.class);
        dictRelMapper = Mockito.mock(DishDictMapper.class);
        dishIngMapper = Mockito.mock(DishIngredientMapper.class);
        historyMapper = Mockito.mock(DishHistoryMapper.class);
        ingredientNutritionMapper = Mockito.mock(IngredientNutritionMapper.class);
        ingredientMapper = Mockito.mock(IngredientMapper.class);
        cookingRecordMapper = Mockito.mock(CookingRecordMapper.class);
        dictMapper = Mockito.mock(DictMapper.class);
        pantryService = Mockito.mock(PantryService.class);
        // search 测试沿用 null dictMapper/pantryService（fillRelNames/stockLevel 走空分支）；
        // saveFull/detail 测试用 buildSvcWithDicts() 构造带 mock 的实例。
        svc = newSvc(null, null);
        injectBaseMapper(svc, dishMapper);
    }

    /** 构造带指定 dictMapper/pantryService 的 DishService（baseMapper 由调用方注入）。 */
    private DishService newSvc(DictMapper dm, PantryService ps) {
        DishService s = new DishService(stepMapper, dictRelMapper, dishIngMapper, historyMapper,
                ingredientNutritionMapper, ingredientMapper, new NutritionCalcService(),
                dm, cookingRecordMapper, ps);
        injectBaseMapper(s, dishMapper);
        return s;
    }

    /** saveFull 测试专用：spy + stub saveOrUpdate（绕过 ServiceImpl 的 TableInfo 缓存依赖，照 MenuServiceTest 范式）。 */
    private DishService newSvcForSave(DictMapper dm, PantryService ps) {
        DishService s = Mockito.spy(newSvc(dm, ps));
        Mockito.doReturn(true).when(s).saveOrUpdate(any(Dish.class));
        return s;
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

    /** 详情：ingredients 里的 ingredientName 应被批量回填（一次 selectBatchIds，无 N+1）。 */
    @Test
    void 详情_食材名批量回填() {
        long dishId = 1L;
        when(dishMapper.selectById(dishId)).thenReturn(dish(dishId, "番茄炒蛋"));
        when(dishIngMapper.selectList(any())).thenReturn(List.of(
                di(10L, bd("200")),  // 番茄 200g
                di(20L, bd("100"))   // 鸡蛋 100g
        ));
        when(dictRelMapper.selectList(any())).thenReturn(List.of());
        when(stepMapper.selectList(any())).thenReturn(List.of());
        // 一次批量查回两个食材名
        when(ingredientMapper.selectBatchIds(any())).thenReturn(List.of(
                ingredient(10L, "番茄"),
                ingredient(20L, "鸡蛋")
        ));

        DishService.DishDetail detail = svc.detail(dishId);

        assertThat(detail.ingredients()).hasSize(2);
        assertThat(detail.ingredients()).extracting(DishIngredient::getIngredientName)
                .containsExactlyInAnyOrder("番茄", "鸡蛋");
        assertThat(detail.ingredients()).extracting(DishIngredient::getAmount)
                .containsExactlyInAnyOrder(bd("200"), bd("100"));
        // 关键：只查了一次 ingredient 表（批量），不是逐个查
        Mockito.verify(ingredientMapper, Mockito.times(1)).selectBatchIds(any());
    }

    /** 详情：食材被软删（查不到名）时 ingredientName 落 null，不报错。 */
    @Test
    void 详情_食材查不到名时为null不报错() {
        long dishId = 1L;
        when(dishMapper.selectById(dishId)).thenReturn(dish(dishId, "孤儿菜"));
        when(dishIngMapper.selectList(any())).thenReturn(List.of(di(99L, bd("50"))));
        when(dictRelMapper.selectList(any())).thenReturn(List.of());
        when(stepMapper.selectList(any())).thenReturn(List.of());
        when(ingredientMapper.selectBatchIds(any())).thenReturn(List.of()); // 食材全删了

        DishService.DishDetail detail = svc.detail(dishId);

        assertThat(detail.ingredients()).hasSize(1);
        assertThat(detail.ingredients().get(0).getIngredientName()).isNull();
    }

    /** cookedCount：无就餐成员时回填 0，不报错（容错验证）。 */
    @Test
    void 无就餐成员_cookedCount回填0不报错() {
        DishSearchDTO q = new DishSearchDTO();
        q.setPageNum(1);
        q.setPageSize(10);

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Dish> mp =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 10);
        mp.setRecords(List.of(dish(1L, "番茄炒蛋"), dish(2L, "黄瓜")));
        mp.setTotal(2);
        when(dishMapper.selectPage(any(), any(Wrapper.class))).thenReturn(mp);
        // 无 session → fillCookedCount 拿不到 member → 回填 0，cookingRecordMapper 不应被调用

        var page = svc.search(q);

        assertThat(page.getRecords()).extracting(Dish::getCookedCount)
                .containsExactly(0, 0); // 无 member，全部回填 0
        Mockito.verify(cookingRecordMapper, Mockito.never()).selectMaps(any());
    }

    // ===================== saveFull（整体替换保存） =====================

    /** saveFull：步骤 + 字典关联 + 食材用量 全部先删后插；用量原文（amount+unitId）原样落库。 */
    @Test
    void saveFull_整体替换_步骤关联食材先删后插() {
        DishService svcWithDicts = newSvcForSave(dictMapper, pantryService);

        Dish dish = dish(1L, "番茄炒蛋");
        DishStep s1 = new DishStep();
        s1.setSeq(1);
        s1.setText("切块");
        DishIngredient ing = new DishIngredient();
        ing.setIngredientId(10L);
        ing.setAmount(bd("200"));
        ing.setUnitId(20L); // 克单位

        DishSaveDTO dto = new DishSaveDTO();
        dto.setDish(dish);
        dto.setSteps(List.of(s1));
        dto.setCuisineIds(List.of(31L));
        dto.setTagIds(List.of(32L));
        dto.setCategoryIds(List.of(33L));
        dto.setIngredients(List.of(ing));

        svcWithDicts.saveFull(dto);

        // 步骤：删 1 次 + 插 1 次
        verify(stepMapper).delete(any());
        verify(stepMapper, times(1)).insert(any());
        // 字典关联：删 1 次 + 插 3 次（cuisine/tag/category 各 1）
        ArgumentCaptor<DishDict> relCaptor = ArgumentCaptor.forClass(DishDict.class);
        verify(dictRelMapper).delete(any());
        verify(dictRelMapper, times(3)).insert(relCaptor.capture());
        assertThat(relCaptor.getAllValues()).extracting(DishDict::getRelType)
                .containsExactlyInAnyOrder("cuisine", "tag", "category");
        // 食材：删 1 次 + 插 1 次；V55 不再换算克，用量原文（amount/unitId）原样落库
        ArgumentCaptor<DishIngredient> ingCaptor = ArgumentCaptor.forClass(DishIngredient.class);
        verify(dishIngMapper).delete(any());
        verify(dishIngMapper, times(1)).insert(ingCaptor.capture());
        assertThat(ingCaptor.getValue().getAmount()).isEqualByComparingTo("200");
        assertThat(ingCaptor.getValue().getUnitId()).isEqualTo(20L);
        assertThat(ingCaptor.getValue().getDishId()).isEqualTo(1L);
    }

    /** saveFull：非克单位用量（如「2个」）原样落库，不再换算克（V55）。 */
    @Test
    void saveFull_非克单位_用量原文原样落库() {
        DishService svcReal = Mockito.spy(newSvc(null, null));
        Mockito.doReturn(true).when(svcReal).saveOrUpdate(any(Dish.class));

        DishIngredient ing = new DishIngredient();
        ing.setIngredientId(10L);
        ing.setAmount(bd("2"));      // 2 个
        ing.setUnitId(22L);          // 「个」
        DishSaveDTO dto = new DishSaveDTO();
        dto.setDish(dish(1L, "x"));
        dto.setIngredients(List.of(ing));

        svcReal.saveFull(dto);

        ArgumentCaptor<DishIngredient> ingCaptor = ArgumentCaptor.forClass(DishIngredient.class);
        verify(dishIngMapper).insert(ingCaptor.capture());
        // 用量原文保留：amount=2、unitId=22（换算表已删，无 grams 计算）
        assertThat(ingCaptor.getValue().getAmount()).isEqualByComparingTo("2");
        assertThat(ingCaptor.getValue().getUnitId()).isEqualTo(22L);
    }

    /** saveFull：null 步骤/关联/食材 → 只删不插（不报错）。 */
    @Test
    void saveFull_null集合_只删不插() {
        DishService svcWithDicts = newSvcForSave(dictMapper, pantryService);

        DishSaveDTO dto = new DishSaveDTO();
        dto.setDish(dish(1L, "空菜"));
        // steps/cuisineIds/tagIds/categoryIds/ingredients 全 null

        svcWithDicts.saveFull(dto);

        verify(stepMapper).delete(any());
        verify(stepMapper, never()).insert(any());
        verify(dictRelMapper).delete(any());
        verify(dictRelMapper, never()).insert(any());
        verify(dishIngMapper).delete(any());
        verify(dishIngMapper, never()).insert(any());
    }

    // ===================== deleteFull：列表左滑删除（错误数据清理） =====================

    /** deleteFull：连带物理清 步骤/关联/用料/编辑历史，主表软删。 */
    @Test
    void deleteFull_连带清理关联表_主表软删() {
        // spy + stub removeById（绕过 ServiceImpl 的 TableInfo 缓存依赖，照 newSvcForSave 范式）
        DishService s = Mockito.spy(newSvc(dictMapper, pantryService));
        Mockito.doReturn(true).when(s).removeById(99L);

        s.deleteFull(99L);

        verify(stepMapper).delete(any());
        verify(dictRelMapper).delete(any());
        verify(dishIngMapper).delete(any());
        verify(historyMapper).delete(any());
        verify(s).removeById(99L);
    }

    // ===================== detail：单位名 + 库存档位回填 =====================

    /** detail：unitName 按 unitId 批量回填（一次 selectBatchIds，无 N+1）。 */
    @Test
    void 详情_单位名批量回填() {
        DishService svcWithDicts = newSvc(dictMapper, pantryService);
        injectBaseMapper(svcWithDicts, dishMapper);
        long dishId = 1L;
        when(dishMapper.selectById(dishId)).thenReturn(dish(dishId, "番茄炒蛋"));
        DishIngredient di = di(10L, bd("200"));
        di.setUnitId(20L); // g
        when(dishIngMapper.selectList(any())).thenReturn(List.of(di));
        when(dictRelMapper.selectList(any())).thenReturn(List.of());
        when(stepMapper.selectList(any())).thenReturn(List.of());
        SysDict unitG = new SysDict();
        unitG.setId(20L);
        unitG.setName("g");
        when(dictMapper.selectBatchIds(any())).thenReturn(List.of(unitG));
        when(ingredientMapper.selectBatchIds(any())).thenReturn(List.of(ingredient(10L, "番茄")));
        when(pantryService.levelMap(anyList())).thenReturn(Map.of(10L, "ENOUGH"));

        DishService.DishDetail detail = svcWithDicts.detail(dishId);

        assertThat(detail.ingredients()).hasSize(1);
        assertThat(detail.ingredients().get(0).getUnitName()).isEqualTo("g");
        assertThat(detail.ingredients().get(0).getStockLevel()).isEqualTo("ENOUGH");
        verify(dictMapper, times(1)).selectBatchIds(any());
    }

    /** detail：无库存数据 → stockLevel 回填 NONE（pantryService.levelMap 返回空 Map）。 */
    @Test
    void 详情_无库存_回填NONE() {
        DishService svcWithDicts = newSvc(dictMapper, pantryService);
        injectBaseMapper(svcWithDicts, dishMapper);
        long dishId = 1L;
        when(dishMapper.selectById(dishId)).thenReturn(dish(dishId, "孤儿菜"));
        DishIngredient di = di(10L, bd("50"));
        di.setUnitId(20L);
        when(dishIngMapper.selectList(any())).thenReturn(List.of(di));
        when(dictRelMapper.selectList(any())).thenReturn(List.of());
        when(stepMapper.selectList(any())).thenReturn(List.of());
        when(dictMapper.selectBatchIds(any())).thenReturn(List.of());
        when(ingredientMapper.selectBatchIds(any())).thenReturn(List.of());
        when(pantryService.levelMap(anyList())).thenReturn(Map.of());

        DishService.DishDetail detail = svcWithDicts.detail(dishId);

        assertThat(detail.ingredients().get(0).getStockLevel()).isEqualTo("NONE");
    }

    /** detail：菜系/标签/分类关联按 relType 分流到 3 个列表。 */
    @Test
    void 详情_关联分流菜系标签分类() {
        DishService svcWithDicts = newSvc(dictMapper, pantryService);
        injectBaseMapper(svcWithDicts, dishMapper);
        long dishId = 1L;
        when(dishMapper.selectById(dishId)).thenReturn(dish(dishId, "x"));
        when(dishIngMapper.selectList(any())).thenReturn(List.of());
        DishDict cuisine = new DishDict();
        cuisine.setDishId(dishId);
        cuisine.setDictId(31L);
        cuisine.setRelType("cuisine");
        DishDict tag = new DishDict();
        tag.setDishId(dishId);
        tag.setDictId(32L);
        tag.setRelType("tag");
        DishDict cat = new DishDict();
        cat.setDishId(dishId);
        cat.setDictId(33L);
        cat.setRelType("category");
        when(dictRelMapper.selectList(any())).thenReturn(List.of(cuisine, tag, cat));
        when(stepMapper.selectList(any())).thenReturn(List.of());

        DishService.DishDetail detail = svcWithDicts.detail(dishId);

        assertThat(detail.cuisineIds()).containsExactly(31L);
        assertThat(detail.tagIds()).containsExactly(32L);
        assertThat(detail.categoryIds()).containsExactly(33L);
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

    private static Ingredient ingredient(long id, String name) {
        Ingredient ing = new Ingredient();
        ing.setId(id);
        ing.setName(name);
        return ing;
    }

    private static IngredientNutrition nut(long metricId, BigDecimal value) {
        IngredientNutrition n = new IngredientNutrition();
        n.setMetricId(metricId);
        n.setValue(value);
        return n;
    }
}
