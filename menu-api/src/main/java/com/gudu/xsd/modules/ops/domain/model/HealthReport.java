package com.gudu.xsd.modules.ops.domain.model;

import java.util.List;

/** 系统健康摘要：各项探测结果 + 汇总状态（全部 ok 才为 ok）。 */
public record HealthReport(String status, List<HealthItem> items) {

    public static HealthReport of(List<HealthItem> items) {
        boolean allOk = items.stream().allMatch(i -> HealthItem.OK.equals(i.status()));
        return new HealthReport(allOk ? HealthItem.OK : HealthItem.ERR, List.copyOf(items));
    }
}
