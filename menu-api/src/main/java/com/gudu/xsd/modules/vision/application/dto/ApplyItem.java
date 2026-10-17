package com.gudu.xsd.modules.vision.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 放进余量的一行（用户确认后的名称/数量/保质期；ingredientId 为空则按名匹配或新建食材）。 */
public record ApplyItem(String name, Long ingredientId, BigDecimal amount, String unit, LocalDate bestBefore) {
}
