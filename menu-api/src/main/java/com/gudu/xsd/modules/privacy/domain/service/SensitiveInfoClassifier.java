package com.gudu.xsd.modules.privacy.domain.service;

import com.gudu.xsd.modules.privacy.domain.model.ConsentDocType;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 成员档案敏感信息判定（B.2，纯函数）：
 * <ul>
 *   <li>健康档案（health_profile 里任一非空值，或体控目标 / 活动水平）→ 需要 {@link ConsentDocType#SENSITIVE_HEALTH}；</li>
 *   <li>不满 14 周岁：age（岁）∈ (0,14)，或 ageMonth（月龄）∈ (0,168)，或特殊人群含「宝宝辅食」
 *       → 另需监护人 {@link ConsentDocType#CHILD_INFO}。</li>
 * </ul>
 * 小程序 utils/sensitiveConsent.ts 有同一规则的镜像（先弹窗再保存）；服务端以本类为准强制执行。
 */
public final class SensitiveInfoClassifier {

    public static final String BABY_AUDIENCE = "宝宝辅食";
    static final int CHILD_AGE = 14;

    private SensitiveInfoClassifier() {
    }

    /** 保存这些字段需要的单独同意（无敏感信息 → 空集）。 */
    public static Set<ConsentDocType> required(Map<String, Object> health, String goal, String activityLevel) {
        Set<ConsentDocType> out = EnumSet.noneOf(ConsentDocType.class);
        if (hasHealthData(health) || notBlank(goal) || notBlank(activityLevel)) out.add(ConsentDocType.SENSITIVE_HEALTH);
        if (isChild(health)) out.add(ConsentDocType.CHILD_INFO);
        return out;
    }

    public static boolean hasHealthData(Map<String, Object> health) {
        if (health == null) return false;
        for (Object v : health.values()) {
            if (isPresent(v)) return true;
        }
        return false;
    }

    public static boolean isChild(Map<String, Object> health) {
        if (health == null) return false;
        double age = number(health.get("age"));
        if (age > 0 && age < CHILD_AGE) return true;
        double months = number(health.get("ageMonth"));
        if (months > 0 && months < CHILD_AGE * 12) return true;
        Object aud = health.get("audiences");
        if (aud instanceof Collection<?> c) {
            for (Object a : c) {
                if (a != null && BABY_AUDIENCE.equals(a.toString().trim())) return true;
            }
        } else if (aud != null && aud.toString().contains(BABY_AUDIENCE)) {
            return true;
        }
        return false;
    }

    private static boolean isPresent(Object v) {
        if (v == null) return false;
        if (v instanceof String s) return !s.isBlank();
        if (v instanceof Collection<?> c) return c.stream().anyMatch(SensitiveInfoClassifier::isPresent);
        if (v instanceof Map<?, ?> m) return m.values().stream().anyMatch(SensitiveInfoClassifier::isPresent);
        return true;
    }

    private static double number(Object v) {
        if (v instanceof Number n) return n.doubleValue();
        if (v == null) return 0;
        try {
            return Double.parseDouble(v.toString().trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
