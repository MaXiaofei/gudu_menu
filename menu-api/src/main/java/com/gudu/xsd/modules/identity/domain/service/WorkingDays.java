package com.gudu.xsd.modules.identity.domain.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 工作日计算（隐私政策「注销后 15 个工作日内删除或匿名化」）：只跳过周六周日，不含法定节假日
 * ——偏严（期限算短），后台提前高亮，宁早勿晚。
 */
public final class WorkingDays {

    /** 隐私政策承诺的注销处理时限。 */
    public static final int DEACTIVATION_LIMIT = 15;

    private WorkingDays() {
    }

    /** from 当天之后第 n 个工作日的当天结束时刻（截止时间）。 */
    public static LocalDateTime deadline(LocalDateTime from, int n) {
        LocalDate d = from.toLocalDate();
        int added = 0;
        while (added < n) {
            d = d.plusDays(1);
            if (d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY) added++;
        }
        return d.atTime(23, 59, 59);
    }

    /**
     * 超期分界：受理时间早于返回值 ⇔ 截止时间已过（截止只取决于受理日期，且随日期单调不减）。
     * 供仓储按受理时间粗筛「已超期」。
     */
    public static LocalDateTime overdueThreshold(LocalDateTime now) {
        LocalDate d = now.toLocalDate();
        while (!now.isAfter(deadline(d.atStartOfDay(), DEACTIVATION_LIMIT))) d = d.minusDays(1);
        return d.plusDays(1).atStartOfDay();
    }

    /** 未完成且已过截止时间。 */
    public static boolean overdue(LocalDateTime requestedAt, boolean done, LocalDateTime now) {
        return !done && requestedAt != null && now.isAfter(deadline(requestedAt, DEACTIVATION_LIMIT));
    }
}
