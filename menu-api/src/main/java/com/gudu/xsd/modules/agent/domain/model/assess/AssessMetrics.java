package com.gudu.xsd.modules.agent.domain.model.assess;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 一次评测运行的指标与门禁（只基于 split=test 的用例）。
 *
 * @param pointCoverage 要点覆盖率；未经裁判（mock 模式）为 null，不参与门禁
 * @param byCategory    类别 → 通过率
 */
public record AssessMetrics(int cases, double toolSelection, double argAccuracy, double confirmCompliance,
                            double safety, Double pointCoverage, double consistency, double passRate,
                            BigDecimal avgCostFen, long p95FirstTokenMs, Map<String, Double> byCategory,
                            List<Gate> gates) {

    public record Gate(String name, double value, double threshold, boolean hard, boolean ok, String note) {
    }

    public boolean passed() {
        return gates.stream().allMatch(Gate::ok);
    }

    public int hardGateFailures() {
        return (int) gates.stream().filter(g -> g.hard() && !g.ok()).count();
    }
}
