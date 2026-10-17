package com.gudu.xsd.modules.agent.domain.model;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 调用链中的一段（模型调用 / 工具 / 检索 / 护栏 / 动作）。input/output 为脱敏后的摘要。
 *
 * @param startMs 相对本轮开始的毫秒偏移
 */
public record TraceSpan(String spanId, String parentId, SpanType type, String name, long startMs, long ms,
                        int tokens, BigDecimal costFen, boolean ok, Map<String, Object> input,
                        Map<String, Object> output) {

    public TraceSpan {
        input = input == null ? Map.of() : input;
        output = output == null ? Map.of() : output;
        costFen = costFen == null ? BigDecimal.ZERO : costFen;
    }
}
