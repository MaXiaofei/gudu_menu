package com.gudu.xsd.modules.agent.application.tools;

import com.gudu.xsd.modules.agent.domain.port.Kitchen;
import com.gudu.xsd.modules.agent.domain.port.Kitchen.DishInfo;
import com.gudu.xsd.modules.agent.domain.port.Kitchen.Lookup;
import com.gudu.xsd.modules.agent.domain.port.Kitchen.MemberInfo;
import com.gudu.xsd.modules.agent.domain.port.Kitchen.MenuInfo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 名称 → 实体解析（菜 / 食集 / 成员），汇总找不到、无权、歧义三类问题，供写工具统一报错或追问。 */
final class NameResolver {

    private NameResolver() {
    }

    record Dishes(List<DishInfo> found, List<String> notFound, List<String> forbidden,
                  Map<String, List<String>> ambiguous) {

        boolean clean() {
            return notFound.isEmpty() && forbidden.isEmpty() && ambiguous.isEmpty();
        }
    }

    static Dishes dishes(Kitchen k, List<String> names) {
        List<DishInfo> found = new ArrayList<>();
        List<String> nf = new ArrayList<>();
        List<String> fb = new ArrayList<>();
        Map<String, List<String>> amb = new LinkedHashMap<>();
        for (String n : names) {
            Lookup<DishInfo> l = k.findDish(n);
            switch (l.status()) {
                case FOUND -> {
                    if (found.stream().noneMatch(d -> d.id() == l.value().id())) found.add(l.value());
                }
                case FORBIDDEN -> fb.add(n);
                case AMBIGUOUS -> amb.put(n, l.candidates().stream().map(DishInfo::name).toList());
                default -> nf.add(n);
            }
        }
        return new Dishes(found, nf, fb, amb);
    }

    /** 统一的解析失败结果；null 表示全部解析成功。 */
    static ToolOutcome dishProblem(Dishes r) {
        if (!r.forbidden().isEmpty()) return ToolOutcome.fail("无权访问或不存在：" + String.join("、", r.forbidden()));
        if (!r.ambiguous().isEmpty()) {
            return ToolOutcome.fail("菜名有歧义，需要用户确认", Map.of("needClarify", true, "candidates", r.ambiguous()));
        }
        if (!r.notFound().isEmpty()) {
            return ToolOutcome.fail("菜谱里没有：" + String.join("、", r.notFound()),
                    Map.of("notFound", r.notFound()));
        }
        return null;
    }

    static ToolOutcome menuProblem(Lookup<MenuInfo> l, String ref) {
        return switch (l.status()) {
            case FOUND -> null;
            case FORBIDDEN -> ToolOutcome.fail("无权访问或不存在：" + ref);
            case AMBIGUOUS -> ToolOutcome.fail("有多个食集匹配，需要用户确认", Map.of("needClarify", true,
                    "candidates", l.candidates().stream().map(MenuInfo::name).toList()));
            default -> ToolOutcome.fail("没有叫「" + ref + "」的食集");
        };
    }

    record Members(List<MemberInfo> found, List<String> unknown) {
    }

    static Members members(Kitchen k, List<String> names) {
        List<MemberInfo> all = k.members();
        List<MemberInfo> found = new ArrayList<>();
        List<String> unknown = new ArrayList<>();
        for (String n : names) {
            Optional<MemberInfo> m = all.stream().filter(x -> x.name().equals(n)).findFirst();
            if (m.isEmpty() && ("我".equals(n) || "自己".equals(n))) m = all.stream().filter(MemberInfo::me).findFirst();
            if (m.isPresent()) {
                if (!found.contains(m.get())) found.add(m.get());
            } else {
                unknown.add(n);
            }
        }
        return new Members(found, unknown);
    }
}
