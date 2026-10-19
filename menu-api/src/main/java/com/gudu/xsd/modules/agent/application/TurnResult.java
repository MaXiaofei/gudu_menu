package com.gudu.xsd.modules.agent.application;

import com.gudu.xsd.modules.agent.domain.model.PendingAction;
import com.gudu.xsd.modules.agent.domain.model.ToolCallRecord;
import com.gudu.xsd.modules.agent.domain.model.TraceSpan;
import com.gudu.xsd.modules.agent.domain.model.TraceStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 一轮对话的结果（落库 / 评测比对用）。
 *
 * @param steps      模型调用次数
 * @param superseded 被本轮新卡片替代的动作 id
 */
public record TurnResult(String text, String reasoning, List<ToolCallRecord> tools,
                         List<Map<String, Object>> toolArgs, List<Map<String, Object>> cards,
                         List<PendingAction> actions, List<String> superseded, List<TraceSpan> spans,
                         TraceStatus status, String provider, String model, boolean degraded, int steps,
                         int promptTokens, int completionTokens, BigDecimal costFen, long ms, Long firstTokenMs,
                         int phoneMasked) {
}
