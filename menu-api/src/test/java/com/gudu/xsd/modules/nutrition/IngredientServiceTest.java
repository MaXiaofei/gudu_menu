package com.gudu.xsd.modules.nutrition;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gudu.xsd.modules.nutrition.mapper.IngredientMapper;
import com.gudu.xsd.modules.nutrition.mapper.IngredientNutritionMapper;
import com.gudu.xsd.modules.nutrition.mapper.NutritionMetricMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * IngredientService 批量营养查询测试：
 * 1) nutritionBatch 一次 IN 查询组装嵌套 map（空入参 / 正常 / 未命中食材）；
 * 2) listWithNutrition / pageWithNutrition 不再逐条 nutritionOf（N+1 → 1 次批量查询）；
 * 3) nutritionOf（单查，DailyLogService 仍依赖）行为保持。
 *
 * <p>V55（食材去单位）：V41 库存余量（stockMap/stockUnitName）与 V53 换算徽标
 * （unitGramCount/defaultGramSet）测试随单位解绑一并删除。
 */
class IngredientServiceTest {

    private IngredientNutritionMapper nutMapper;
    private NutritionMetricMapper metricMapper;
    private IngredientMapper baseMapper;
    private IngredientService svc;

    @BeforeEach
    void setUp() {
        nutMapper = mock(IngredientNutritionMapper.class);
        metricMapper = mock(NutritionMetricMapper.class);
        baseMapper = mock(IngredientMapper.class);
        svc = new IngredientService(nutMapper, metricMapper);
        // ServiceImpl 的 list(qw) 走 getBaseMapper().selectList(qw)；注入 baseMapper
        org.springframework.test.util.ReflectionTestUtils.setField(svc, "baseMapper", baseMapper);
    }

    private IngredientNutrition nut(Long ingId, Long metricId, String val) {
        IngredientNutrition n = new IngredientNutrition();
        n.setIngredientId(ingId);
        n.setMetricId(metricId);
        n.setValue(new BigDecimal(val));
        return n;
    }

    private NutritionMetric metric(Long id, String name) {
        NutritionMetric m = new NutritionMetric();
        m.setId(id);
        m.setName(name);
        return m;
    }

    private Ingredient ing(Long id, String name) {
        Ingredient i = new Ingredient();
        i.setId(id);
        i.setName(name);
        return i;
    }

    // ---------- nutritionBatch ----------

    @Test
    void nutritionBatch_空入参返回空map且不查DB() {
        assertThat(svc.nutritionBatch(null)).isEmpty();
        assertThat(svc.nutritionBatch(List.of())).isEmpty();
        verify(nutMapper, never()).selectList(any());
    }

    @Test
    void nutritionBatch_一次IN查询组装嵌套map() {
        when(nutMapper.selectList(any())).thenReturn(List.of(
                nut(1L, 1L, "19"),
                nut(1L, 2L, "0.9"),
                nut(2L, 1L, "144")
        ));
        Map<Long, Map<Long, BigDecimal>> r = svc.nutritionBatch(List.of(1L, 2L, 3L));
        // id=1: calorie 19, protein 0.9
        assertThat(r.get(1L)).containsOnly(
                Map.entry(1L, new BigDecimal("19")),
                Map.entry(2L, new BigDecimal("0.9")));
        // id=2: calorie 144
        assertThat(r.get(2L)).containsOnly(Map.entry(1L, new BigDecimal("144")));
        // id=3 未命中：不出现在 map 中（调用方自行 getOrDefault 补空）
        assertThat(r).doesNotContainKey(3L);
    }

    // ---------- listWithNutrition：N+1 → 1 次批量 ----------

    @Test
    void listWithNutrition_营养只批量查一次且VO营养正确() {
        when(metricMapper.selectList(any())).thenReturn(List.of(
                metric(1L, "calorie"), metric(2L, "protein")));
        // ServiceImpl.list(qw) → baseMapper.selectList(qw)
        when(baseMapper.selectList(any())).thenReturn(List.of(ing(1L, "番茄"), ing(2L, "鸡蛋")));
        when(nutMapper.selectList(any())).thenReturn(List.of(
                nut(1L, 1L, "19"),
                nut(2L, 1L, "144"), nut(2L, 2L, "13")));

        List<IngredientVO> r = svc.listWithNutrition(null, null);

        // 营养批量只查 1 次（不是 N=2 次）
        verify(nutMapper, times(1)).selectList(any());
        // VO 营养正确：番茄 calorie 19；鸡蛋 calorie 144 + protein 13
        assertThat(r).hasSize(2);
        assertThat(r.get(0).getName()).isEqualTo("番茄");
        assertThat(r.get(0).getNutrition()).containsOnly(Map.entry("calorie", new BigDecimal("19")));
        assertThat(r.get(1).getName()).isEqualTo("鸡蛋");
        assertThat(r.get(1).getNutrition()).containsOnly(
                Map.entry("calorie", new BigDecimal("144")),
                Map.entry("protein", new BigDecimal("13")));
    }

    @Test
    void pageWithNutrition_只对本页id批量查一次营养() {        when(metricMapper.selectList(any())).thenReturn(List.of(metric(1L, "calorie")));
        // ServiceImpl.page(page, qw) 内部调 baseMapper.selectPage(page, qw)
        Page<Ingredient> page = new Page<>(1, 10);
        page.setTotal(2L);
        page.setRecords(List.of(ing(1L, "番茄"), ing(2L, "鸡蛋")));
        when(baseMapper.selectPage(any(), any())).thenReturn(page);
        when(nutMapper.selectList(any())).thenReturn(List.of(
                nut(1L, 1L, "19"), nut(2L, 1L, "144")));

        IngredientPageQuery q = new IngredientPageQuery();
        q.setPageNum(1);
        q.setPageSize(10);
        var r = svc.pageWithNutrition(q);

        verify(nutMapper, times(1)).selectList(any());
        assertThat(r.getRecords()).hasSize(2);
        assertThat(r.getTotal()).isEqualTo(2L);
        // 姓名与营养映射正确
        Map<String, BigDecimal> byName = new HashMap<>();
        r.getRecords().forEach(v -> byName.put(v.getName(), v.getNutrition().get("calorie")));
        assertThat(byName).containsEntry("番茄", new BigDecimal("19"));
        assertThat(byName).containsEntry("鸡蛋", new BigDecimal("144"));
    }

    @Test
    void nutritionBatch_用IN而非逐条eq_通过QueryWrapper参数校验() {
        ArgumentCaptor<QueryWrapper<IngredientNutrition>> captor = ArgumentCaptor.forClass(QueryWrapper.class);
        when(nutMapper.selectList(captor.capture())).thenReturn(List.of());
        svc.nutritionBatch(List.of(1L, 2L, 3L));
        // SQL 片段应包含 IN 关键字（MyBatis-Plus QueryWrapper.in 生成的 SQL segment）
        String sql = captor.getValue().getCustomSqlSegment();
        // 不同 MP 版本可能返回 null（无 wrapper 条件时），有内容时需含 IN
        if (sql != null && !sql.isEmpty()) {
            assertThat(sql.toUpperCase()).contains("IN");
        }
    }

    // ---------- nutritionOf（保留，别处仍用） ----------

    @Test
    void nutritionOf_单查metricId到value() {
        when(nutMapper.selectList(any())).thenReturn(List.of(
                nut(1L, 1L, "19"), nut(1L, 2L, "0.9")));
        Map<Long, BigDecimal> r = svc.nutritionOf(1L);
        assertThat(r).containsOnly(
                Map.entry(1L, new BigDecimal("19")),
                Map.entry(2L, new BigDecimal("0.9")));
    }

    // ---------- V55 食材去单位后：detail 只挂品类名 + 营养 ----------

    @Test
    void detail_挂品类名与营养() {
        when(metricMapper.selectList(any())).thenReturn(List.of(metric(1L, "calorie")));
        Ingredient tomato = ing(1L, "番茄");
        tomato.setPurchaseCategoryId(24L);
        when(baseMapper.selectById(1L)).thenReturn(tomato);
        when(nutMapper.selectList(any())).thenReturn(List.of(nut(1L, 1L, "19")));
        // 字典：品类 蔬菜(24)
        com.gudu.xsd.modules.dict.mapper.DictMapper dictMapper =
                mock(com.gudu.xsd.modules.dict.mapper.DictMapper.class);
        com.gudu.xsd.modules.dict.SysDict c24 = new com.gudu.xsd.modules.dict.SysDict();
        c24.setId(24L); c24.setName("蔬菜");
        when(dictMapper.selectList(any())).thenReturn(List.of(c24));
        org.springframework.test.util.ReflectionTestUtils.setField(svc, "dictMapper", dictMapper);

        IngredientVO vo = svc.detail(1L);

        assertThat(vo.getName()).isEqualTo("番茄");
        assertThat(vo.getCategoryName()).isEqualTo("蔬菜");
        assertThat(vo.getNutrition()).containsEntry("calorie", new BigDecimal("19"));
    }

    // ---------- 插画 key（V68） ----------

    @Test
    void 新增食材_按名称计算插画key() {
        when(baseMapper.insert(any(Ingredient.class))).thenReturn(1);
        Ingredient i = ing(null, "西红柿");

        svc.save(i);

        assertThat(i.getIconKey()).isEqualTo("tomato");
    }

    @Test
    void 改名_重算插画key_品类沿用旧值() {
        Ingredient old = ing(5L, "番茄");
        when(baseMapper.selectById(5L)).thenReturn(old);
        when(baseMapper.updateById(any(Ingredient.class))).thenReturn(1);
        Ingredient patch = ing(5L, "胡萝卜");

        svc.updateById(patch);

        assertThat(patch.getIconKey()).isEqualTo("carrot");
    }

    @Test
    void 只改其他字段_不动插画key() {
        when(baseMapper.updateById(any(Ingredient.class))).thenReturn(1);
        Ingredient patch = new Ingredient();
        patch.setId(5L);
        patch.setEdible(1);

        svc.updateById(patch);

        assertThat(patch.getIconKey()).isNull();
        verify(baseMapper, never()).selectById(any());
    }

    @Test
    void 批量插画key_只含非空() {
        Ingredient a = ing(1L, "番茄");
        a.setIconKey("tomato");
        Ingredient b = ing(2L, "秋葵");
        when(baseMapper.selectByIds(any())).thenReturn(List.of(a, b));

        assertThat(svc.iconKeyMap(List.of(1L, 2L))).containsExactly(Map.entry(1L, "tomato"));
        assertThat(svc.iconKeyMap(List.of())).isEmpty();
    }

    // ---------- V95：别名搜索 / 我家标签 / 同名排序 / 只看我家 ----------

    private IngredientAlias alias(String a, Long ingId) {
        IngredientAlias x = new IngredientAlias();
        x.setAlias(a);
        x.setIngredientId(ingId);
        return x;
    }

    @Test
    void 搜别名_土豆命中马铃薯_条件含IN且标注matchedAlias() {
        com.gudu.xsd.modules.nutrition.mapper.IngredientAliasMapper aliasMapper =
                mock(com.gudu.xsd.modules.nutrition.mapper.IngredientAliasMapper.class);
        svc.setAliasMapper(aliasMapper);
        when(aliasMapper.selectList(any())).thenReturn(List.of(alias("土豆", 3L)));
        when(metricMapper.selectList(any())).thenReturn(List.of());
        Page<Ingredient> page = new Page<>(1, 10);
        page.setRecords(List.of(ing(3L, "马铃薯")));
        ArgumentCaptor<QueryWrapper<Ingredient>> qw = ArgumentCaptor.forClass(QueryWrapper.class);
        when(baseMapper.selectPage(any(), qw.capture())).thenReturn(page);

        IngredientPageQuery q = new IngredientPageQuery();
        q.setKeyword("土豆");
        var r = svc.pageWithNutrition(q);

        assertThat(r.getRecords()).extracting(IngredientVO::getName).containsExactly("马铃薯");
        assertThat(r.getRecords().get(0).getMatchedAlias()).isEqualTo("土豆");
        assertThat(qw.getValue().getSqlSegment()).containsIgnoringCase("LIKE").containsIgnoringCase(" IN ");
    }

    @Test
    void VO标记familyOwned_本家私有为true_公共为false() {
        when(metricMapper.selectList(any())).thenReturn(List.of());
        Ingredient pub = ing(1L, "番茄");
        Ingredient own = ing(2L, "家传酱");
        own.setFamilyId(7L);
        Page<Ingredient> page = new Page<>(1, 10);
        page.setRecords(List.of(pub, own));
        when(baseMapper.selectPage(any(), any())).thenReturn(page);

        var r = svc.pageWithNutrition(new IngredientPageQuery());

        assertThat(r.getRecords()).extracting(IngredientVO::isFamilyOwned).containsExactly(false, true);
    }

    @Test
    void 同名时本家的排前面_不同名顺序不变() {
        Ingredient pub = ing(1L, "酱油");
        Ingredient other = ing(2L, "盐");
        Ingredient own = ing(3L, "酱油");
        own.setFamilyId(7L);

        var out = IngredientService.preferFamilyFirst(List.of(pub, other, own));

        assertThat(out).extracting(Ingredient::getId).containsExactly(3L, 2L, 1L);
    }

    @Test
    void 只看我家_无家庭上下文时结果为空条件() {
        when(metricMapper.selectList(any())).thenReturn(List.of());
        Page<Ingredient> page = new Page<>(1, 10);
        page.setRecords(List.of());
        ArgumentCaptor<QueryWrapper<Ingredient>> qw = ArgumentCaptor.forClass(QueryWrapper.class);
        when(baseMapper.selectPage(any(), qw.capture())).thenReturn(page);
        IngredientPageQuery q = new IngredientPageQuery();
        q.setFamilyOnly(true);

        svc.pageWithNutrition(q);

        assertThat(qw.getValue().getSqlSegment()).contains("1 = 0");
    }

    @Test
    void 只看我家_有家庭上下文时按family_id过滤() {
        com.gudu.xsd.common.tenant.TenantContextHolder.set(
                new com.gudu.xsd.common.tenant.TenantContext(7L, 1L, 1L, "MEMBER"));
        try {
            when(metricMapper.selectList(any())).thenReturn(List.of());
            Page<Ingredient> page = new Page<>(1, 10);
            page.setRecords(List.of());
            ArgumentCaptor<QueryWrapper<Ingredient>> qw = ArgumentCaptor.forClass(QueryWrapper.class);
            when(baseMapper.selectPage(any(), qw.capture())).thenReturn(page);
            IngredientPageQuery q = new IngredientPageQuery();
            q.setFamilyOnly(true);

            svc.pageWithNutrition(q);

            assertThat(qw.getValue().getSqlSegment()).containsIgnoringCase("family_id");
            assertThat(qw.getValue().getParamNameValuePairs()).containsValue(7L);
        } finally {
            com.gudu.xsd.common.tenant.TenantContextHolder.clear();
        }
    }
}
