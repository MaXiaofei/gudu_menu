package com.gudu.xsd.modules.agent.application.tools;

import com.gudu.xsd.modules.agent.domain.port.Kitchen.MemberInfo;
import com.gudu.xsd.modules.agent.domain.service.ProfileConstraintPolicy;
import com.gudu.xsd.modules.ai.gateway.domain.model.ToolDef;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** get_family_profile：成员与饮食约束。 */
public class GetFamilyProfileTool implements AgentTool {

    public static final String NAME = "get_family_profile";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String label() {
        return "读取家庭档案";
    }

    @Override
    public boolean write() {
        return false;
    }

    @Override
    public ToolDef definition() {
        return new ToolDef(NAME, "读取家庭成员和每个人的饮食约束（宝宝辅食、高血压限钠、高血脂限脂等）。",
                ToolSupport.schema(Map.of()));
    }

    @Override
    public ToolOutcome run(ToolContext ctx, Map<String, Object> args) {
        List<MemberInfo> members = ctx.kitchen().members();
        List<Map<String, Object>> items = members.stream().map(m -> {
            ProfileConstraintPolicy.Constraint c = ProfileConstraintPolicy.of(List.of(m));
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("name", m.name());
            if (m.me()) x.put("me", true);
            if (m.age() != null) x.put("age", m.age());
            x.put("tags", m.tags());
            x.put("avoidTags", List.copyOf(c.excludeTags()));
            x.put("preferTags", List.copyOf(c.preferTags()));
            return x;
        }).toList();
        List<String> notes = ProfileConstraintPolicy.of(members).notes();
        String summary = members.size() + " 位成员" + (notes.isEmpty() ? "" : "：" + String.join("；", notes));
        return ToolOutcome.ok(summary, Map.of("members", items), List.of());
    }
}
