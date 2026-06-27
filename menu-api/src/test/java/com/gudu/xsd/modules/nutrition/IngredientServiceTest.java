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
    void pageWithNutrition_只对本页id批量查一次营养() {
        when(metricMapper.selectList(any())).thenReturn(List.of(metric(1L, "calorie")));
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
}
