package com.gudu.xsd.modules.pantry.application.dto;

import java.time.LocalDate;

/** 临期项（GET /pantry/expiring、/recommend/home.expiring）。 */
public record ExpiringItemView(Long ingredientId, String ingredientName, String level,
                               LocalDate bestBefore, Integer daysLeft) {
}
