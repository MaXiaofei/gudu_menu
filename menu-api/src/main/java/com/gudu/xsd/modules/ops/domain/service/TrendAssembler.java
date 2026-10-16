package com.gudu.xsd.modules.ops.domain.service;

import com.gudu.xsd.modules.ops.domain.model.DailyCounts;
import com.gudu.xsd.modules.ops.domain.model.OpsStats.TrendPoint;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** 趋势补齐（纯函数）：[today-days+1, today] 每天一点，缺数据记 0，日期升序。 */
public final class TrendAssembler {

    public static final int MAX_DAYS = 90;

    public static int clampDays(int days) {
        return Math.max(1, Math.min(days, MAX_DAYS));
    }

    public List<TrendPoint> assemble(LocalDate today, int days, DailyCounts counts) {
        int n = clampDays(days);
        List<TrendPoint> out = new ArrayList<>(n);
        for (int i = n - 1; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            out.add(new TrendPoint(d,
                    counts.newAccounts().getOrDefault(d, 0L),
                    counts.activeMembers().getOrDefault(d, 0L),
                    counts.cookRecords().getOrDefault(d, 0L),
                    counts.aiCalls().getOrDefault(d, 0L)));
        }
        return out;
    }

    /** 统计窗口起点：today 往前 days-1 天的 0 点。 */
    public static LocalDate windowStart(LocalDate today, int days) {
        return today.minusDays(clampDays(days) - 1L);
    }
}
