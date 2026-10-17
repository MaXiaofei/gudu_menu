package com.gudu.xsd.modules.agent.domain.model.assess;

import java.time.LocalDateTime;

/**
 * 库内评测用例（后台导入 / 差评回流生成的草稿）。json 为与 JSONL 同格式的一行。
 *
 * @param status DRAFT（待人工标注期望，不参与运行）/ ACTIVE
 */
public record StoredCase(Long id, String caseId, String split, String category, String json, String source,
                         String fromTraceId, String status, LocalDateTime createdAt) {
}
