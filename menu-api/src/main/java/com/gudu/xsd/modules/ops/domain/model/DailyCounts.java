package com.gudu.xsd.modules.ops.domain.model;

import java.time.LocalDate;
import java.util.Map;

/** 按日聚合的原始计数（缺失日期 = 0，由 TrendAssembler 补齐）。 */
public record DailyCounts(Map<LocalDate, Long> newAccounts, Map<LocalDate, Long> activeMembers,
                          Map<LocalDate, Long> cookRecords, Map<LocalDate, Long> aiCalls) {

    public DailyCounts {
        newAccounts = newAccounts == null ? Map.of() : Map.copyOf(newAccounts);
        activeMembers = activeMembers == null ? Map.of() : Map.copyOf(activeMembers);
        cookRecords = cookRecords == null ? Map.of() : Map.copyOf(cookRecords);
        aiCalls = aiCalls == null ? Map.of() : Map.copyOf(aiCalls);
    }
}
