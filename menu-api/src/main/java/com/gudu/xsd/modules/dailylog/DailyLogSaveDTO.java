package com.gudu.xsd.modules.dailylog;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 每日饮食记录提交请求体：一条日志 + 多个摄入明细。
 * 每条 item：dishId 与 ingredientId 二选一，amount 是克(食材)/份数(菜品)。
 */
@Data
public class DailyLogSaveDTO {

    @NotNull
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate date;

    /** 备注，可为空。 */
    private String note;

    /** 摄入明细，可为空（仅记备注）。 */
    private List<Item> items;

    @Data
    public static class Item {
        /** 摄入菜品（与 ingredientId 二选一）。 */
        private Long dishId;
        /** 摄入食材（与 dishId 二选一）。 */
        private Long ingredientId;
        /** 数量：克(ingredient) / 份数(dish)。 */
        private BigDecimal amount;
        /** 份数缩放系数（dish 项，默认 1）。 */
        private BigDecimal servingFactor;
    }
}
