package com.gudu.xsd.modules.ops;

import com.gudu.xsd.modules.ops.application.OpsStatsAppService;
import com.gudu.xsd.modules.ops.domain.model.DailyCounts;
import com.gudu.xsd.modules.ops.domain.model.OpsStats;
import com.gudu.xsd.modules.ops.domain.repository.OpsStatsQuery;
import com.gudu.xsd.modules.ops.domain.service.TrendAssembler;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 运营仪表盘：趋势补齐（纯函数）+ 应用服务窗口计算（Mockito）。 */
class OpsStatsTest {

    static final LocalDate TODAY = LocalDate.of(2026, 10, 16);

    @Test
    void 趋势_按天补零_日期升序_days夹紧() {
        DailyCounts c = new DailyCounts(Map.of(TODAY, 2L), Map.of(TODAY.minusDays(1), 5L),
                Map.of(TODAY.minusDays(2), 1L), null);
        List<OpsStats.TrendPoint> t = new TrendAssembler().assemble(TODAY, 3, c);
        assertThat(t).extracting(OpsStats.TrendPoint::date)
                .containsExactly(TODAY.minusDays(2), TODAY.minusDays(1), TODAY);
        assertThat(t.get(2).newAccounts()).isEqualTo(2);
        assertThat(t.get(1).activeMembers()).isEqualTo(5);
        assertThat(t.get(0).cookRecords()).isEqualTo(1);
        assertThat(t.get(0).aiCalls()).isZero();
        assertThat(TrendAssembler.clampDays(0)).isEqualTo(1);
        assertThat(TrendAssembler.clampDays(1000)).isEqualTo(TrendAssembler.MAX_DAYS);
        assertThat(TrendAssembler.windowStart(TODAY, 7)).isEqualTo(TODAY.minusDays(6));
        assertThat(new TrendAssembler().assemble(TODAY, 500, new DailyCounts(null, null, null, null)))
                .hasSize(TrendAssembler.MAX_DAYS);
    }

    @Test
    void 应用服务_近7天窗口与趋势窗口() {
        OpsStatsQuery q = mock(OpsStatsQuery.class);
        OpsStats.Totals totals = new OpsStats.Totals(10, 4, 50, 30, 2);
        OpsStats.Last7d last = new OpsStats.Last7d(1, 3, 6, 9, new BigDecimal("0.12"));
        when(q.totals()).thenReturn(totals);
        when(q.summarySince(TODAY.minusDays(6))).thenReturn(last);
        when(q.dailySince(TODAY.minusDays(13))).thenReturn(new DailyCounts(Map.of(), Map.of(), Map.of(), Map.of()));
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        OpsStatsAppService svc = new OpsStatsAppService(q, Clock.fixed(TODAY.atTime(10, 0).atZone(zone).toInstant(), zone));

        OpsStats s = svc.stats(14);

        assertThat(s.totals()).isEqualTo(totals);
        assertThat(s.last7d()).isEqualTo(last);
        assertThat(s.trend()).hasSize(14);
        assertThat(s.trend().get(13).date()).isEqualTo(TODAY);
        verify(q).dailySince(TODAY.minusDays(13));
    }
}
