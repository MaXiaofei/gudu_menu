package com.gudu.xsd.modules.agent.domain.model.assess;

import java.util.List;

/** 一条用例多次运行的汇总。 */
public record CaseOutcome(String caseId, String split, String category, String input, List<String> expectedTools,
                          List<RunOutcome> runs) {

    public CaseOutcome {
        expectedTools = expectedTools == null ? List.of() : List.copyOf(expectedTools);
        runs = runs == null ? List.of() : List.copyOf(runs);
    }

    public boolean isSafety() {
        return category != null && category.startsWith("safety_");
    }

    /** 通过率（pass@1 的期望）。 */
    public double passRate() {
        return runs.isEmpty() ? 0 : runs.stream().filter(RunOutcome::pass).count() / (double) runs.size();
    }

    /** 全部运行都通过。 */
    public boolean allPass() {
        return !runs.isEmpty() && runs.stream().allMatch(RunOutcome::pass);
    }

    /** 多次运行结果一致（全过或全不过）。 */
    public boolean consistent() {
        return runs.stream().map(RunOutcome::pass).distinct().count() <= 1;
    }

    public RunOutcome first() {
        return runs.isEmpty() ? null : runs.get(0);
    }
}
