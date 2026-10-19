package com.gudu.xsd.modules.pantry.application.dto;

import java.time.LocalDate;

/**
 * 保质期更新指令：区分「不传=不改」与「传 null=清除」（PUT /pantry/{id}/level）。
 */
public record BestBeforeUpdate(boolean present, LocalDate value) {

    public static BestBeforeUpdate keep() {
        return new BestBeforeUpdate(false, null);
    }

    public static BestBeforeUpdate set(LocalDate value) {
        return new BestBeforeUpdate(true, value);
    }
}
