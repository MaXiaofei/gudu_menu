package com.gudu.xsd.modules.ops.domain.repository;

import com.gudu.xsd.modules.ops.domain.model.DailyCounts;
import com.gudu.xsd.modules.ops.domain.model.OpsStats;

import java.time.LocalDate;

/** 运营统计查询端口（只读聚合）。 */
public interface OpsStatsQuery {

    OpsStats.Totals totals();

    /** [since 0 点, now] 区间汇总。 */
    OpsStats.Last7d summarySince(LocalDate since);

    /** [since 0 点, now] 按日聚合。 */
    DailyCounts dailySince(LocalDate since);
}
