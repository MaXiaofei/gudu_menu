package com.gudu.xsd.modules.vision.domain.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 数量文本解析（模型常返回中文数量）：「两个」→ 2 个、「半盒」→ 0.5 盒、「一个半」→ 1.5 个、
 * 「x6」「×6」→ 6、「6个」→ 6 个、「十二个」→ 12 个、「适量」→ 1（无单位）。解析不了返回 1。
 */
public final class ChineseQuantityParser {

    public record Quantity(BigDecimal amount, String unit) {
    }

    private static final Map<Character, Integer> DIGITS = Map.ofEntries(
            Map.entry('零', 0), Map.entry('〇', 0), Map.entry('一', 1), Map.entry('壹', 1), Map.entry('二', 2),
            Map.entry('两', 2), Map.entry('俩', 2), Map.entry('三', 3), Map.entry('四', 4), Map.entry('五', 5),
            Map.entry('六', 6), Map.entry('七', 7), Map.entry('八', 8), Map.entry('九', 9));

    private static final Pattern ARABIC = Pattern.compile("^[x×*X]?\\s*(\\d+(?:\\.\\d+)?)\\s*(.*)$");
    private static final Pattern CHINESE = Pattern.compile("^([零〇一壹二两俩三四五六七八九十百半]+)(.*)$");
    private static final int MAX_UNIT = 8;

    private ChineseQuantityParser() {
    }

    public static Quantity parse(String text) {
        if (text == null) return new Quantity(BigDecimal.ONE, null);
        String t = text.trim().replace("约", "").replace("大概", "").trim();
        if (t.isEmpty() || t.startsWith("适量") || t.startsWith("少许")) return new Quantity(BigDecimal.ONE, null);
        Matcher a = ARABIC.matcher(t);
        if (a.matches()) {
            BigDecimal v = new BigDecimal(a.group(1));
            String unit = cleanUnit(a.group(2));
            if (unit != null && unit.length() > 1 && unit.endsWith("半")) {
                v = v.add(new BigDecimal("0.5"));
                unit = cleanUnit(unit.substring(0, unit.length() - 1));
            }
            return new Quantity(positiveOrOne(v), unit);
        }
        Matcher c = CHINESE.matcher(t);
        if (c.matches()) {
            BigDecimal v = chineseNumber(c.group(1));
            String rest = c.group(2) == null ? "" : c.group(2).trim();
            if (rest.length() > 1 && rest.endsWith("半")) { // 「一个半」：单位后跟「半」
                v = v.add(new BigDecimal("0.5"));
                rest = rest.substring(0, rest.length() - 1);
            }
            return new Quantity(positiveOrOne(v), cleanUnit(rest));
        }
        return new Quantity(BigDecimal.ONE, t.length() <= 2 ? t : null);
    }

    /** 中文数字（≤ 999，含「半」「十二」「二十」「两百」）。 */
    static BigDecimal chineseNumber(String s) {
        int total = 0;
        int current = 0;
        boolean half = false;
        for (char ch : s.toCharArray()) {
            if (ch == '半') {
                half = true;
            } else if (ch == '百') {
                total += (current == 0 ? 1 : current) * 100;
                current = 0;
            } else if (ch == '十') {
                total += (current == 0 ? 1 : current) * 10;
                current = 0;
            } else if (DIGITS.containsKey(ch)) {
                current = DIGITS.get(ch);
            }
        }
        BigDecimal v = BigDecimal.valueOf(total + (long) current);
        return half ? v.add(new BigDecimal("0.5")) : v;
    }

    private static BigDecimal positiveOrOne(BigDecimal v) {
        return v == null || v.signum() <= 0 ? BigDecimal.ONE
                : v.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros();
    }

    private static String cleanUnit(String u) {
        if (u == null) return null;
        String s = u.trim();
        if (s.isEmpty()) return null;
        return s.length() > MAX_UNIT ? s.substring(0, MAX_UNIT) : s;
    }
}
