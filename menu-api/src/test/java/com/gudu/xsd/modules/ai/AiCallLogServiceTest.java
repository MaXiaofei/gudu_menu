package com.gudu.xsd.modules.ai;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gudu.xsd.common.PageQuery;
import com.gudu.xsd.modules.ai.mapper.AiCallLogMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** AiCallLogService：网关行计入、业务行判重、成本换算。 */
class AiCallLogServiceTest {

    private static final LocalDateTime T = LocalDateTime.of(2026, 11, 1, 12, 0, 0);

    private static AiCallLog row(String scene, Long member, String capability, String provider, String status,
                                 String cost, String costFen, LocalDateTime at) {
        AiCallLog r = new AiCallLog();
        r.setScene(scene);
        r.setMemberId(member);
        r.setCapability(capability);
        r.setProvider(provider);
        r.setStatus(status);
        r.setCost(cost == null ? null : new BigDecimal(cost));
        r.setCostFen(costFen == null ? null : new BigDecimal(costFen));
        r.setTokensIn(10);
        r.setTokensOut(5);
        r.setLatencyMs(100);
        r.setCreateTime(at);
        return r;
    }

    @Test
    void costYuan_costFen优先_缺失用cost_都空为零() {
        assertThat(AiCallLogService.costYuan(row("a", 1L, "CHAT", "p", "ok", "9", "78.9", T)))
                .isEqualByComparingTo("0.789");
        assertThat(AiCallLogService.costYuan(row("a", 1L, null, "p", "ok", "0.0009", null, T)))
                .isEqualByComparingTo("0.0009");
        assertThat(AiCallLogService.costYuan(row("a", 1L, null, "p", "ok", null, null, T))).isEqualByComparingTo("0");
    }

    @Test
    void counted_网关行全留_含降级失败尝试() {
        List<AiCallLog> in = List.of(
                row("mealplan", 1L, "CHAT", "deepseek", "fail", "0", null, T),
                row("mealplan", 1L, "CHAT", "glm", "ok", "0.01", "1", T.plusSeconds(2)));
        assertThat(AiCallLogService.counted(in)).hasSize(2);
    }

    @Test
    void counted_业务行与时间窗内同场景同成员网关行判重_只计一次() {
        AiCallLog gw = row("nutrition_fill", 7L, "CHAT", "deepseek", "ok", "0.02", "2", T);
        AiCallLog biz = row("nutrition_fill", 7L, null, "deepseek", "ok", "0", null, T.plusSeconds(3));
        AiCallLog otherMember = row("nutrition_fill", 8L, null, "deepseek", "ok", "0", null, T.plusSeconds(3));
        AiCallLog tooLate = row("nutrition_fill", 7L, null, "deepseek", "ok", "0", null, T.plusMinutes(10));
        List<AiCallLog> out = AiCallLogService.counted(List.of(gw, biz, otherMember, tooLate));
        assertThat(out).containsExactly(gw, otherMember, tooLate);
    }

    @Test
    void counted_不调模型的向量推荐业务行永远保留() {
        AiCallLog gw = row("menu_recommend", 1L, "CHAT", "deepseek", "ok", null, "1", T);
        AiCallLog biz = row("menu_recommend", 1L, null, "vector", "ok", "0", null, T.plusSeconds(1));
        assertThat(AiCallLogService.counted(List.of(gw, biz))).hasSize(2);
    }

    @Test
    void usage_包含网关行_成本按元汇总_并去重() {
        AiCallLogMapper mapper = mock(AiCallLogMapper.class);
        LocalDateTime now = LocalDateTime.now();
        when(mapper.selectList(any())).thenReturn(List.of(
                row("mealplan", 1L, "CHAT", "deepseek", "ok", "0.0000", "78.9", now.minusMinutes(5)),
                row("mealplan", 1L, "CHAT", "deepseek", "fail", null, null, now.minusMinutes(6)),
                row("menu_recommend", 1L, null, "vector", "ok", "0", null, now.minusMinutes(1)),
                row("agent", 2L, "CHAT", "deepseek", "ok", "0.0294", "2.95", now.minusMinutes(2)),
                // 与上面 agent 网关行重复的业务行，不计
                row("agent", 2L, null, "deepseek", "ok", "0", null, now.minusMinutes(2).plusSeconds(1))));
        List<Map<String, Object>> res = new AiCallLogService(mapper).usage(7);
        Map<String, Map<String, Object>> by = new java.util.HashMap<>();
        res.forEach(m -> by.put((String) m.get("scene"), m));
        assertThat(by).containsOnlyKeys("mealplan", "menu_recommend", "agent");
        assertThat(by.get("mealplan").get("totalCalls")).isEqualTo(2);
        assertThat(by.get("mealplan").get("failCalls")).isEqualTo(1);
        assertThat((BigDecimal) by.get("mealplan").get("costYuan")).isEqualByComparingTo("0.789");
        assertThat(by.get("agent").get("totalCalls")).isEqualTo(1);
        assertThat((BigDecimal) by.get("agent").get("costYuan")).isEqualByComparingTo("0.0295");
        assertThat((BigDecimal) by.get("menu_recommend").get("costYuan")).isEqualByComparingTo("0");
    }

    @Test
    @SuppressWarnings("unchecked")
    void page_不再排除网关行_并回填costYuan() {
        AiCallLogMapper mapper = mock(AiCallLogMapper.class);
        Page<AiCallLog> p = new Page<>(1, 15);
        p.setRecords(List.of(row("mealplan", 1L, "CHAT", "deepseek", "ok", "0", "78.9", T)));
        when(mapper.selectPage(any(), any())).thenReturn(p);
        IPage<AiCallLog> res = new AiCallLogService(mapper).page(new PageQuery(), "mealplan", null);
        ArgumentCaptor<QueryWrapper<AiCallLog>> cap = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(mapper).selectPage(any(), cap.capture());
        String sql = cap.getValue().getSqlSegment();
        assertThat(sql).doesNotContain("capability IS NULL").contains("capability IS NOT NULL");
        assertThat(res.getRecords().get(0).getCostYuan()).isEqualByComparingTo("0.789");
    }
}
