package com.gudu.xsd.modules.family.application;

import com.gudu.xsd.common.privacy.PersonalDataSection;
import com.gudu.xsd.common.privacy.PersonalDataSubject;
import com.gudu.xsd.modules.family.domain.model.Family;
import com.gudu.xsd.modules.family.domain.model.FamilyMemberProfile;
import com.gudu.xsd.modules.family.domain.model.Membership;
import com.gudu.xsd.modules.family.domain.repository.FamilyRepository;
import com.gudu.xsd.modules.family.domain.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 导出我的数据（C.2）：所属家庭（名称、我的角色、加入时间、成员数）与<b>本人</b>在该家庭的成员档案（含本人健康档案）。
 * 不导出其他家庭成员的档案——其健康 / 儿童信息属于他人的敏感个人信息。
 */
@Component
@RequiredArgsConstructor
public class FamilyPersonalData implements PersonalDataSection {

    private final FamilyRepository families;
    private final ProfileRepository profiles;

    @Override
    public String key() {
        return "families";
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    public Object export(PersonalDataSubject s) {
        Map<Long, FamilyMemberProfile> mine = new LinkedHashMap<>();
        for (FamilyMemberProfile p : profiles.findByAccount(s.accountId())) mine.putIfAbsent(p.familyId().value(), p);
        List<Family> list = families.findActiveByAccount(s.accountId());
        return list.stream().map(f -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("familyId", f.id());
            m.put("name", f.name().value());
            m.put("myRole", f.roleOf(s.accountId()).map(Enum::name).orElse(null));
            m.put("joinedAt", f.memberships().stream().filter(x -> Objects.equals(x.accountId(), s.accountId()))
                    .map(Membership::joinedAt).findFirst().orElse(null));
            m.put("memberCount", f.memberships().size());
            FamilyMemberProfile p = mine.get(f.id());
            m.put("myProfile", p == null ? null : profile(p));
            return m;
        }).toList();
    }

    private static Map<String, Object> profile(FamilyMemberProfile p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("profileId", p.id());
        m.put("displayName", p.displayName());
        m.put("avatarUrl", p.avatarUrl());
        m.put("healthProfile", p.health() == null ? null : p.health().attributes());
        m.put("goal", p.goal());
        m.put("activityLevel", p.activityLevel());
        return m;
    }
}
