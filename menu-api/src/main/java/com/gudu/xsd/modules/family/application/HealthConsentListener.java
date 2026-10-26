package com.gudu.xsd.modules.family.application;

import com.gudu.xsd.modules.family.domain.model.FamilyMemberProfile;
import com.gudu.xsd.modules.family.domain.repository.ProfileRepository;
import com.gudu.xsd.modules.identity.domain.event.AccountMerged;
import com.gudu.xsd.modules.privacy.domain.event.SensitiveConsentWithdrawn;
import com.gudu.xsd.modules.privacy.domain.model.ConsentDocType;
import com.gudu.xsd.modules.privacy.domain.service.SensitiveInfoClassifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 撤回单独同意即删除对应字段（mini-release-compliance B.2，隐私政策 §二「撤回后我们会删除相应信息」）。
 *
 * <p>删除范围 = 该账号<b>本人</b>的档案（各家庭）+ 健康信息由该账号提供的档案（member.health_consent_account_id）：
 * <ul>
 *   <li>撤回 sensitive_health → 上述档案的健康档案、体控目标、活动水平全部清空；</li>
 *   <li>撤回 child_info → 其中被判定为不满 14 周岁（年龄 / 月龄 / 宝宝辅食）的档案的上述字段清空。</li>
 * </ul>
 * 档案本身（称呼、头像）不删——它们属于家庭共享数据，可在家庭成员页另行删除。与撤回记录同一事务。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HealthConsentListener {

    private final ProfileRepository profiles;

    @EventListener
    public void onWithdrawn(SensitiveConsentWithdrawn e) {
        Map<Long, FamilyMemberProfile> targets = new LinkedHashMap<>();
        profiles.findByAccount(e.accountId()).forEach(p -> targets.put(p.id(), p));
        profiles.findHealthProvidedBy(e.accountId()).forEach(p -> targets.putIfAbsent(p.id(), p));
        int cleared = 0;
        for (FamilyMemberProfile p : targets.values()) {
            if (!hasHealth(p)) continue;
            if (e.docType() == ConsentDocType.CHILD_INFO
                    && !SensitiveInfoClassifier.isChild(p.health() == null ? null : p.health().attributes())) {
                continue;
            }
            p.updateHealth(null, null, null);
            profiles.save(p);
            cleared++;
        }
        log.info("账号 {} 撤回单独同意 {}：清除 {} 份档案的健康信息", e.accountId(), e.docType().code(), cleared);
    }

    @EventListener
    public void onMerged(AccountMerged e) {
        profiles.reassignHealthProvider(e.sourceAccountId(), e.targetAccountId());
    }

    private static boolean hasHealth(FamilyMemberProfile p) {
        return (p.health() != null && !p.health().isEmpty()) || p.goal() != null || p.activityLevel() != null;
    }
}
