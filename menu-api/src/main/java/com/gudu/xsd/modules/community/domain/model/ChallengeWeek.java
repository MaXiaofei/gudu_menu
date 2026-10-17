package com.gudu.xsd.modules.community.domain.model;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

/** 话题挑战周期（值对象）：一周 = 周一至周日。 */
public record ChallengeWeek(LocalDate monday, LocalDate sunday) {

    public static ChallengeWeek of(LocalDate anyDay) {
        LocalDate mon = anyDay.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return new ChallengeWeek(mon, mon.plusDays(6));
    }

    public boolean contains(LocalDate d) {
        return d != null && !d.isBefore(monday) && !d.isAfter(sunday);
    }

    public List<LocalDate> days() {
        List<LocalDate> out = new ArrayList<>(7);
        for (int i = 0; i < 7; i++) out.add(monday.plusDays(i));
        return out;
    }

    /** 从 startDate 起的第几周（1 起）；startDate 为空或晚于本周时返回 1。 */
    public int weekNumberSince(LocalDate startDate) {
        if (startDate == null) return 1;
        LocalDate startMon = of(startDate).monday();
        long weeks = java.time.temporal.ChronoUnit.WEEKS.between(startMon, monday);
        return (int) Math.max(1, weeks + 1);
    }
}
