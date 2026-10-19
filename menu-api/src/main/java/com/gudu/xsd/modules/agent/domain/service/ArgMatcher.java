package com.gudu.xsd.modules.agent.domain.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 评测参数匹配器（纯函数，作用于 Map / List / String / Number / Boolean 组成的 JSON 树）。
 *
 * <p>期望值形式：字面值（等于；实参为数组时表示包含）或对象
 * {eq | in | contains | notContains | containsAll | containsAny | gte | lte | regex | any}，
 * 一个对象里多个算子同时满足（AND）。路径用点号，列表上的非数字路径段对每个元素取值并展开
 * （{@code items[].name} 与 {@code items.name} 等价 → 所有采购项名称）。字符串比较忽略首尾空白。
 */
public final class ArgMatcher {

    private static final List<String> OPS = List.of("eq", "in", "contains", "notContains", "containsAll", "containsAny",
            "gte", "lte", "regex", "any");

    private ArgMatcher() {
    }

    /** 期望 Map（路径 → 期望）全部满足；返回未满足的路径（空 = 全部满足）。 */
    public static List<String> mismatches(Object actualRoot, Map<String, Object> expected) {
        List<String> bad = new ArrayList<>();
        if (expected == null) return bad;
        for (Map.Entry<String, Object> e : expected.entrySet()) {
            Object actual = resolve(actualRoot, e.getKey());
            if (!matches(actual, e.getValue())) bad.add(e.getKey());
        }
        return bad;
    }

    /** 点号路径取值；不存在返回 null。 */
    public static Object resolve(Object root, String path) {
        if (path == null || path.isEmpty()) return root;
        Object cur = root;
        for (String seg : path.split("\\.")) {
            cur = step(cur, seg);
            if (cur == null) return null;
        }
        return cur;
    }

    private static Object step(Object cur, String rawSeg) {
        String seg = rawSeg.endsWith("[]") ? rawSeg.substring(0, rawSeg.length() - 2) : rawSeg;
        if (cur instanceof Map<?, ?> m) return m.get(seg);
        if (cur instanceof List<?> l) {
            if (seg.matches("\\d+")) {
                int i = Integer.parseInt(seg);
                return i < l.size() ? l.get(i) : null;
            }
            List<Object> out = new ArrayList<>();
            for (Object o : l) {
                Object v = step(o, seg);
                if (v instanceof List<?> inner) out.addAll(inner);
                else if (v != null) out.add(v);
            }
            return out.isEmpty() ? null : out;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public static boolean matches(Object actual, Object expected) {
        if (expected instanceof Map<?, ?> em && isOperatorObject((Map<String, Object>) em)) {
            for (Map.Entry<String, Object> op : ((Map<String, Object>) em).entrySet()) {
                if (!apply(op.getKey(), actual, op.getValue())) return false;
            }
            return true;
        }
        if (actual instanceof Collection<?> c && !(expected instanceof Collection<?>)) {
            return c.stream().anyMatch(x -> eq(x, expected));
        }
        return eq(actual, expected);
    }

    private static boolean isOperatorObject(Map<String, Object> m) {
        return !m.isEmpty() && OPS.containsAll(m.keySet());
    }

    private static boolean apply(String op, Object actual, Object arg) {
        return switch (op) {
            case "eq" -> eq(actual, arg);
            case "in" -> arg instanceof Collection<?> c && (actual instanceof Collection<?> ac
                    ? !ac.isEmpty() && ac.stream().allMatch(a -> c.stream().anyMatch(x -> eq(a, x)))
                    : c.stream().anyMatch(x -> eq(actual, x)));
            case "contains" -> contains(actual, arg);
            case "notContains" -> !contains(actual, arg);
            case "containsAll" -> arg instanceof Collection<?> c && c.stream().allMatch(x -> contains(actual, x));
            case "containsAny" -> arg instanceof Collection<?> c && c.stream().anyMatch(x -> contains(actual, x));
            case "gte" -> compare(actual, arg) != null && compare(actual, arg) >= 0;
            case "lte" -> compare(actual, arg) != null && compare(actual, arg) <= 0;
            case "regex" -> actual != null && Pattern.compile(String.valueOf(arg)).matcher(text(actual)).find();
            case "any" -> Boolean.FALSE.equals(arg) ? actual == null : actual != null;
            default -> false;
        };
    }

    private static boolean contains(Object actual, Object item) {
        if (actual == null) return false;
        if (actual instanceof Collection<?> c) return c.stream().anyMatch(x -> eq(x, item) || textContains(x, item));
        return text(actual).contains(text(item));
    }

    private static boolean textContains(Object x, Object item) {
        return x instanceof String s && item != null && s.contains(text(item));
    }

    static boolean eq(Object a, Object b) {
        if (a == null || b == null) return a == b;
        BigDecimal na = num(a);
        BigDecimal nb = num(b);
        if (na != null && nb != null) return na.compareTo(nb) == 0;
        if (a instanceof Collection<?> ca && b instanceof Collection<?> cb) {
            if (ca.size() != cb.size()) return false;
            List<?> la = new ArrayList<>(ca);
            List<?> lb = new ArrayList<>(cb);
            for (int i = 0; i < la.size(); i++) if (!eq(la.get(i), lb.get(i))) return false;
            return true;
        }
        if (a instanceof Map<?, ?> || b instanceof Map<?, ?>) return a.equals(b);
        return text(a).equals(text(b));
    }

    private static Integer compare(Object a, Object b) {
        BigDecimal na = num(a);
        BigDecimal nb = num(b);
        if (na == null || nb == null) return null;
        return na.compareTo(nb);
    }

    private static BigDecimal num(Object o) {
        if (o instanceof Number n) return new BigDecimal(n.toString());
        if (o instanceof String s && s.trim().matches("-?\\d+(\\.\\d+)?")) return new BigDecimal(s.trim());
        return null;
    }

    private static String text(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }
}
