package com.gudu.xsd.modules.vision.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 对齐字典、分级后的识别项（契约 VisionJob.items[]）。
 *
 * @param name                展示名（已匹配为字典名，否则同 rawName）
 * @param rawName             模型原始名
 * @param ingredientId        匹配到的食材 id（未匹配为 null，入库时按名新建）
 * @param iconKey             插画 key（可空）
 * @param suggestedBestBefore 按品类建议的保质期（可空）
 */
public record RecognizedItem(String name, String rawName, Long ingredientId, String iconKey,
                             BigDecimal amount, String unit, double confidence, ConfidenceLevel level,
                             List<String> alternatives, LocalDate suggestedBestBefore) {

    public RecognizedItem {
        alternatives = alternatives == null ? List.of() : List.copyOf(alternatives);
    }
}
