package com.gudu.xsd.modules.pantry.application.dto;

import java.time.LocalDate;

/**
 * 临期项（GET /pantry/expiring、/recommend/home.expiring）。
 *
 * @param iconKey 插画 key（V68，可空：客户端退首字）
 */
public record ExpiringItemView(Long ingredientId, String ingredientName, String level,
                               LocalDate bestBefore, Integer daysLeft, String iconKey) {

    /** 无插画 key 的便捷构造（测试与旧调用方）。 */
    public ExpiringItemView(Long ingredientId, String ingredientName, String level,
                            LocalDate bestBefore, Integer daysLeft) {
        this(ingredientId, ingredientName, level, bestBefore, daysLeft, null);
    }
}
