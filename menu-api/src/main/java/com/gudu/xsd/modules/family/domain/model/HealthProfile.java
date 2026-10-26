package com.gudu.xsd.modules.family.domain.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康档案值对象：存量为自由 JSON（身高 height(cm) / 体重 weight(kg) / 年龄 age / 性别 gender / 忌口 / 过敏 /
 * 特殊人群：高血压 | 高血糖 | 高血脂 | 宝宝辅食 …），这里只对营养计算用到的字段提供强类型读取，其余原样保留。
 */
public record HealthProfile(Map<String, Object> attributes) {

    public HealthProfile {
        attributes = attributes == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
    }

    public static HealthProfile empty() {
        return new HealthProfile(Map.of());
    }

    public boolean isEmpty() {
        return attributes.isEmpty();
    }

    public double weightKg() {
        return toDouble(attributes.get("weight"));
    }

    public double heightCm() {
        return toDouble(attributes.get("height"));
    }

    public int age() {
        return (int) toDouble(attributes.get("age"));
    }

    /** male / female；缺省按 male（与旧 MemberController 行为一致）。 */
    public String gender() {
        Object g = attributes.get("gender");
        return g == null ? "male" : g.toString();
    }

    public boolean hasBodyMetrics() {
        return attributes.get("weight") != null && attributes.get("height") != null && attributes.get("age") != null
                && weightKg() > 0 && heightCm() > 0 && age() > 0;
    }

    private static double toDouble(Object v) {
        if (v == null) return 0;
        if (v instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(v.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
