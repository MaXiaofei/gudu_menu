package com.gudu.xsd.modules.mealplan.application;

import com.gudu.xsd.common.BizException;
import com.gudu.xsd.modules.mealplan.domain.model.HouseholdProfile;
import com.gudu.xsd.modules.mealplan.domain.model.MemberHealth;
import com.gudu.xsd.modules.member.Member;
import com.gudu.xsd.modules.member.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 成员档案 → 排餐/营养约束。读 member.healthProfile：audiences（特殊人群）、allergies（忌口）、
 * constraints.sodiumMaxMg（钠上限）。multi-tenant-family 落地前「家庭」= 全部成员。
 */
@Component
@RequiredArgsConstructor
public class HouseholdAssembler {

    private final MemberService memberService;

    public record Household(List<Member> members, HouseholdProfile profile, List<String> constraintLabels) {
    }

    public List<Member> members(Collection<Long> memberIds) {
        List<Long> ids = memberIds == null ? List.of() : memberIds.stream().filter(Objects::nonNull).distinct().toList();
        List<Member> out = ids.isEmpty() ? memberService.list() : memberService.listByIds(ids);
        if (out.isEmpty()) throw new BizException("没有找到就餐成员");
        return out;
    }

    public Member member(Long memberId) {
        Member m = memberId == null ? null : memberService.getById(memberId);
        if (m == null) throw new BizException("成员不存在");
        return m;
    }

    /** 合并多个成员的档案；钠上限取最严格。 */
    public HouseholdProfile merge(List<Member> members) {
        Set<String> audiences = new LinkedHashSet<>();
        Set<String> allergies = new LinkedHashSet<>();
        int limit = Integer.MAX_VALUE;
        for (Member m : members) {
            MemberHealth h = health(m);
            audiences.addAll(h.audiences());
            allergies.addAll(h.allergies());
            limit = Math.min(limit, h.asProfile().sodiumLimitMg());
        }
        return new HouseholdProfile(audiences, allergies,
                limit == Integer.MAX_VALUE ? HouseholdProfile.DEFAULT_SODIUM_LIMIT_MG : limit);
    }

    /** 约束标签（原型 chips）：4 口人 / 妈妈少盐 / 林林少油 / 朵朵辅食版 / 忌口 … */
    public List<String> labels(List<Member> members, int servings) {
        List<String> out = new ArrayList<>();
        out.add(servings + " 口人");
        for (Member m : members) {
            MemberHealth h = health(m);
            String name = m.getName() == null ? "家人" : m.getName();
            if (h.has(HouseholdProfile.HYPERTENSION)) out.add(name + "少盐");
            if (h.has(HouseholdProfile.HYPERLIPIDEMIA)) out.add(name + "少油");
            if (h.has(HouseholdProfile.BABY)) out.add(name + "辅食版");
            if (h.has(HouseholdProfile.VEGETARIAN)) out.add(name + "吃素");
            if (!h.allergies().isEmpty()) out.add(name + "忌口 " + String.join("、", h.allergies()));
        }
        return out;
    }

    public MemberHealth health(Member m) {
        Map<String, Object> hp = m.getHealthProfile() == null ? Map.of() : m.getHealthProfile();
        Integer sodium = null;
        if (hp.get("constraints") instanceof Map<?, ?> c && c.get("sodiumMaxMg") instanceof Number n) {
            sodium = n.intValue();
        }
        return new MemberHealth(strings(hp.get("audiences")), strings(hp.get("allergies")), sodium, null, null, null);
    }

    public String profileLabel(Member m) {
        Set<String> a = health(m).audiences();
        return a.isEmpty() ? "普通" : String.join("、", a);
    }

    private static Set<String> strings(Object v) {
        Set<String> out = new LinkedHashSet<>();
        if (v instanceof Collection<?> c) {
            for (Object o : c) {
                if (o != null && !o.toString().isBlank()) out.add(o.toString().trim());
            }
        }
        return out;
    }
}
