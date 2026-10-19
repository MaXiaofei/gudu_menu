package com.gudu.xsd.modules.ai.gateway.domain.model;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * 高峰时段：时段内按原价，其余时段乘 {@code offPeakRatio}（DeepSeek 为 0.5）。
 *
 * @param timezone     时区（DeepSeek 为 Asia/Shanghai）
 * @param weekdaysOnly 仅工作日（周一至周五）有高峰
 * @param ranges       时段，形如 {@code 09:00-12:00}，左闭右开
 * @param offPeakRatio 非高峰价格系数
 */
public record PeakHours(String timezone, boolean weekdaysOnly, List<String> ranges, BigDecimal offPeakRatio) {

    /** DeepSeek：北京时间工作日 9–12、14–18 高峰，其余半价。 */
    public static PeakHours deepSeek() {
        return new PeakHours("Asia/Shanghai", true, List.of("09:00-12:00", "14:00-18:00"), new BigDecimal("0.5"));
    }

    public PeakHours {
        ranges = ranges == null ? List.of() : List.copyOf(ranges);
        offPeakRatio = offPeakRatio == null ? BigDecimal.ONE : offPeakRatio;
        if (timezone == null || timezone.isBlank()) timezone = "Asia/Shanghai";
        ZoneId.of(timezone);
        for (String r : ranges) parse(r);
    }

    /** 该时刻是否高峰。 */
    public boolean isPeak(Instant at) {
        ZonedDateTime t = at.atZone(ZoneId.of(timezone));
        if (weekdaysOnly && (t.getDayOfWeek() == DayOfWeek.SATURDAY || t.getDayOfWeek() == DayOfWeek.SUNDAY)) {
            return false;
        }
        LocalTime lt = t.toLocalTime();
        for (String r : ranges) {
            LocalTime[] p = parse(r);
            if (!lt.isBefore(p[0]) && lt.isBefore(p[1])) return true;
        }
        return false;
    }

    /** 该时刻的价格系数。 */
    public BigDecimal ratioAt(Instant at) {
        return isPeak(at) ? BigDecimal.ONE : offPeakRatio;
    }

    private static LocalTime[] parse(String range) {
        String[] p = range == null ? new String[0] : range.split("-");
        if (p.length != 2) throw new IllegalArgumentException("时段格式应为 HH:mm-HH:mm：" + range);
        return new LocalTime[]{LocalTime.parse(p[0].trim()), LocalTime.parse(p[1].trim())};
    }
}
