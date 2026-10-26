package com.gudu.xsd.modules.identity.application.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 注销处理记录视图（运营后台）。
 *
 * @param deadline 隐私政策时限（受理后第 15 个工作日结束）
 * @param overdue  未完成且已超期（后台高亮）
 */
public record DeactivationView(Long id, Long accountId, LocalDateTime requestedAt, LocalDateTime deadline,
                               String status, List<Step> steps, int attempts, String lastError,
                               LocalDateTime completedAt, boolean overdue) {

    public record Step(String name, boolean ok, String detail) {
    }
}
