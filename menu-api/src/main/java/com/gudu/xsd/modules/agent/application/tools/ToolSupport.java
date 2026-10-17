package com.gudu.xsd.modules.agent.application.tools;

import com.gudu.xsd.modules.agent.domain.port.Kitchen;
import com.gudu.xsd.modules.agent.domain.port.Kitchen.DishInfo;
import com.gudu.xsd.modules.agent.domain.port.Kitchen.MemberInfo;
import com.gudu.xsd.modules.agent.domain.service.ProfileConstraintPolicy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 工具共用：参数读取、JSON Schema 片段、菜卡、画像约束合并。 */
final class ToolSupport {

    private ToolSupport() {
    }

    // ------------------------------------------------------------- 参数

    static String str(Map<String, Object> args, String key) {
        Object v = args == null ? null : args.get(key);
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }

    static Integer integer(Map<String, Object> args, String key) {
        Object v = args == null ? null : args.get(key);
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s && s.trim().matches("-?\\d+")) return Integer.parseInt(s.trim());
        return null;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> map(Map<String, Object> args, String key) {
        Object v = args == null ? null : args.get(key);
        return v instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    static List<String> strings(Map<String, Object> args, String key) {
        Object v = args == null ? null : args.get(key);
        List<String> out = new ArrayList<>();
        if (v instanceof Collection<?> c) {
            for (Object o : c) if (o != null && !String.valueOf(o).isBlank()) out.add(String.valueOf(o).trim());
        } else if (v instanceof String s && !s.isBlank()) {
            for (String p : s.split("[,，、]")) if (!p.isBlank()) out.add(p.trim());
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> objects(Map<String, Object> args, String key) {
        Object v = args == null ? null : args.get(key);
        List<Map<String, Object>> out = new ArrayList<>();
        if (v instanceof Collection<?> c) {
            for (Object o : c) {
                if (o instanceof Map<?, ?> m) out.add((Map<String, Object>) m);
                else if (o != null && !String.valueOf(o).isBlank()) out.add(Map.of("name", String.valueOf(o).trim()));
            }
        }
        return out;
    }

    // ------------------------------------------------------------- Schema

    static Map<String, Object> schema(Map<String, Object> properties, String... required) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("type", "object");
        s.put("properties", properties);
        if (required.length > 0) s.put("required", List.of(required));
        return s;
    }

    static Map<String, Object> prop(String type, String description) {
        return Map.of("type", type, "description", description);
    }

    static Map<String, Object> stringArray(String description) {
        return Map.of("type", "array", "items", Map.of("type", "string"), "description", description);
    }

    static Map<String, Object> enumProp(String description, String... values) {
        return Map.of("type", "string", "enum", List.of(values), "description", description);
    }

    // ------------------------------------------------------------- 卡片

    /** contracts.md DishCard。 */
    static Map<String, Object> dishCard(DishInfo d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", d.id());
        m.put("name", d.name());
        m.put("coverUrl", d.coverUrl());
        m.put("minutes", d.minutes());
        m.put("difficulty", d.difficulty());
        Kitchen.Coverage c = d.coverage();
        m.put("coverage", c == null ? Map.of("have", 0, "total", 0, "missing", List.of())
                : Map.of("have", c.have(), "total", c.total(), "missing", c.missing()));
        m.put("tags", d.tags());
        return m;
    }

    /** 回传模型的菜信息（精简）。 */
    static Map<String, Object> dishForModel(DishInfo d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", d.name());
        if (d.minutes() != null) m.put("minutes", d.minutes());
        m.put("tags", d.tags());
        if (d.sodiumMg() != null) m.put("sodiumMg", d.sodiumMg());
        if (d.coverage() != null) {
            m.put("have", d.coverage().have());
            m.put("total", d.coverage().total());
            m.put("missing", d.coverage().missing());
        }
        return m;
    }

    static Map<String, Object> recipesCard(List<DishInfo> dishes) {
        return Map.of("type", "recipes", "items", dishes.stream().map(ToolSupport::dishCard).toList());
    }

    // ------------------------------------------------------------- 画像

    /** 服务端强制约束：forMembers ∪ 用户原话里点名的成员。 */
    static ProfileConstraintPolicy.Constraint forcedConstraint(ToolContext ctx, List<String> forMembers) {
        List<MemberInfo> members = ctx.kitchen().members();
        Set<MemberInfo> picked = new LinkedHashSet<>(ProfileConstraintPolicy.mentionedIn(ctx.userText(), members));
        for (String n : forMembers) {
            for (MemberInfo m : members) {
                if (m.name().equals(n) || (ProfileConstraintPolicy.isBaby(m) && (n.contains("宝宝") || n.contains("孩子")))) {
                    picked.add(m);
                }
            }
        }
        return ProfileConstraintPolicy.of(picked);
    }

    static String joinNames(List<DishInfo> dishes) {
        return String.join("、", dishes.stream().map(DishInfo::name).toList());
    }
}
