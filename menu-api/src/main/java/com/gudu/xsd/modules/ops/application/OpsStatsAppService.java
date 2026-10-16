package com.gudu.xsd.modules.ops.application;

import com.gudu.xsd.modules.ops.domain.model.OpsStats;
import com.gudu.xsd.modules.ops.domain.repository.OpsStatsQuery;
import com.gudu.xsd.modules.ops.domain.service.TrendAssembler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;

/** 运营仪表盘（G12，超管）：总量 + 近 7 天 + 按日趋势（days 1..90，默认 14）。 */
@Service
@RequiredArgsConstructor
public class OpsStatsAppService {

    static final int LAST_DAYS = 7;

    private final OpsStatsQuery query;
    private final Clock clock;
    private final TrendAssembler assembler = new TrendAssembler();

    public OpsStats stats(int days) {
        LocalDate today = LocalDate.now(clock);
        int n = TrendAssembler.clampDays(days);
        return new OpsStats(query.totals(),
                query.summarySince(TrendAssembler.windowStart(today, LAST_DAYS)),
                assembler.assemble(today, n, query.dailySince(TrendAssembler.windowStart(today, n))));
    }
}
