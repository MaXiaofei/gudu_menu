package com.gudu.xsd.modules.ops.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 运营仪表盘（读模型，契约 §8）。 */
public record OpsStats(Totals totals, Last7d last7d, List<TrendPoint> trend) {

    public record Totals(long members, long accounts, long dishes, long cookRecords, long feedbackOpen) {
    }

    public record Last7d(long newAccounts, long activeMembers, long cookRecords, long aiCalls, BigDecimal aiCostYuan) {
    }

    public record TrendPoint(LocalDate date, long newAccounts, long activeMembers, long cookRecords, long aiCalls) {
    }
}
