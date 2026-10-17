package com.gudu.xsd.modules.agent.domain.service;

import com.gudu.xsd.modules.agent.domain.port.Kitchen.MemberInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 家庭画像 → 菜谱约束（纯函数）。服务端强制合并：模型即使漏传，搜索与推荐也会排除这些标签（双保险）。
 *
 * <ul>
 *   <li>宝宝辅食 / 3 岁以下：排除 辣、重口、油炸、生冷；偏好 宝宝可吃、软烂</li>
 *   <li>高血压：排除 重口、高盐、腌制；偏好 少盐</li>
 *   <li>高血脂：排除 油炸、肥腻；偏好 少油、控脂</li>
 *   <li>高血糖：排除 高糖、甜品；偏好 控糖</li>
 * </ul>
 */
public final class ProfileConstraintPolicy {

    private ProfileConstraintPolicy() {
    }

    public record Constraint(Set<String> excludeTags, Set<String> preferTags, List<String> notes) {
    }

    public static boolean isBaby(MemberInfo m) {
        return (m.age() != null && m.age() < 3) || m.tags().stream()
                .anyMatch(t -> !t.contains(":") && (t.contains("宝宝") || t.contains("辅食")));
    }

    public static Constraint of(Collection<MemberInfo> members) {
        Set<String> exclude = new LinkedHashSet<>();
        Set<String> prefer = new LinkedHashSet<>();
        List<String> notes = new ArrayList<>();
        for (MemberInfo m : members) {
            for (String t : m.tags()) {
                if (t.startsWith("avoid:")) exclude.add(t.substring(6));
                if (t.startsWith("prefer:")) prefer.add(t.substring(7));
            }
            if (isBaby(m)) {
                exclude.addAll(List.of("辣", "重口", "油炸", "生冷"));
                prefer.addAll(List.of("宝宝可吃", "软烂"));
                notes.add(m.name() + "：宝宝辅食，不辣、少盐、软烂");
            }
            if (has(m, "高血压") || has(m, "限钠")) {
                exclude.addAll(List.of("重口", "高盐", "腌制"));
                prefer.add("少盐");
                notes.add(m.name() + "：高血压，限钠少盐");
            }
            if (has(m, "高血脂")) {
                exclude.addAll(List.of("油炸", "肥腻"));
                prefer.addAll(List.of("少油", "控脂"));
                notes.add(m.name() + "：高血脂，少油控脂");
            }
            if (has(m, "高血糖")) {
                exclude.addAll(List.of("高糖", "甜品"));
                prefer.add("控糖");
                notes.add(m.name() + "：高血糖，控糖");
            }
        }
        return new Constraint(exclude, prefer, notes);
    }

    /** 文本里点名到的成员（「给朵朵」「妈妈要少盐」）。 */
    public static List<MemberInfo> mentionedIn(String text, Collection<MemberInfo> members) {
        if (text == null || text.isBlank()) return List.of();
        List<MemberInfo> out = new ArrayList<>();
        for (MemberInfo m : members) {
            if (text.contains(m.name())) out.add(m);
            else if (isBaby(m) && (text.contains("宝宝") || text.contains("孩子"))) out.add(m);
        }
        return out;
    }

    private static boolean has(MemberInfo m, String tag) {
        return m.tags().stream().anyMatch(t -> !t.contains(":") && t.contains(tag));
    }
}
