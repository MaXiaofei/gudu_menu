package com.gudu.xsd.modules.pantry.application.dto;

import java.time.LocalDate;

/** 余量保质期视图（grouped/item 回填 bestBefore + daysLeft）。 */
public record StockShelfView(LocalDate bestBefore, Integer daysLeft) {
}
