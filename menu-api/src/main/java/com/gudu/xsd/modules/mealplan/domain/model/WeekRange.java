package com.gudu.xsd.modules.mealplan.domain.model;

import com.gudu.xsd.common.domain.DomainException;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

/** 一周 = 周一至周日。 */
public record WeekRange(LocalDate start) {

    public static final int DAYS = 7;

    public WeekRange {
        if (start == null) throw new DomainException("起始日期不能为空");
        if (start.getDayOfWeek() != DayOfWeek.MONDAY) throw new DomainException("一周从周一开始，weekStart 必须是周一");
    }

    /** 默认周：今天是周一则本周，否则下周一开始。 */
    public static WeekRange defaultFor(LocalDate today) {
        return new WeekRange(today.getDayOfWeek() == DayOfWeek.MONDAY ? today
                : today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)));
    }

    public LocalDate end() {
        return start.plusDays(DAYS - 1L);
    }

    public List<LocalDate> days() {
        List<LocalDate> out = new ArrayList<>(DAYS);
        for (int i = 0; i < DAYS; i++) out.add(start.plusDays(i));
        return out;
    }

    public boolean contains(LocalDate d) {
        return d != null && !d.isBefore(start) && !d.isAfter(end());
    }

    public static boolean isWeekday(LocalDate d) {
        DayOfWeek w = d.getDayOfWeek();
        return w != DayOfWeek.SATURDAY && w != DayOfWeek.SUNDAY;
    }

    /** 周一 … 周日。 */
    public static String weekdayLabel(LocalDate d) {
        return switch (d.getDayOfWeek()) {
            case MONDAY -> "周一";
            case TUESDAY -> "周二";
            case WEDNESDAY -> "周三";
            case THURSDAY -> "周四";
            case FRIDAY -> "周五";
            case SATURDAY -> "周六";
            case SUNDAY -> "周日";
        };
    }
}
