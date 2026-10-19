package com.gudu.xsd.modules.agent.domain.model.assess;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 评测运行记录。reportJson 为完整报告（指标、门禁、逐条结果、基线对比）。
 *
 * @param status RUNNING / DONE / FAILED
 */
public record StoredRun(Long id, String promptVersion, String provider, String status, Double toolAccuracy,
                        Double argAccuracy, Double pointCoverage, BigDecimal avgCostFen, Long p95Ms, Boolean passed,
                        Integer hardGateFailures, boolean baseline, String reportJson, String error,
                        LocalDateTime createdAt, LocalDateTime finishedAt) {
}
