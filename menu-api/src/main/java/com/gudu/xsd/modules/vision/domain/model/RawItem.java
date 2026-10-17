package com.gudu.xsd.modules.vision.domain.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * 模型原始识别项（解析后、对齐字典前）。
 *
 * @param name         模型给出的名称
 * @param amount       数量（解析失败为 1）
 * @param unit         单位（可空）
 * @param confidence   置信度 0..1（已归一化）
 * @param alternatives 「也可能是」候选
 * @param priceFen     小票价格（分，可空）
 */
public record RawItem(String name, BigDecimal amount, String unit, double confidence,
                      List<String> alternatives, Integer priceFen) {

    public RawItem {
        alternatives = alternatives == null ? List.of() : List.copyOf(alternatives);
    }
}
