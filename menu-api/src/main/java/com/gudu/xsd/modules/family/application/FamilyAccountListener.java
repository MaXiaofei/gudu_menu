package com.gudu.xsd.modules.family.application;

import com.gudu.xsd.common.domain.DomainEventPublisher;
import com.gudu.xsd.modules.family.domain.event.MemberJoined;
import com.gudu.xsd.modules.family.domain.model.Family;
import com.gudu.xsd.modules.family.domain.model.FamilyMemberProfile;
import com.gudu.xsd.modules.family.domain.repository.FamilyRepository;
import com.gudu.xsd.modules.family.domain.repository.ProfileRepository;
import com.gudu.xsd.modules.identity.application.AccountQueryApi;
import com.gudu.xsd.modules.identity.application.dto.AccountView;
import com.gudu.xsd.modules.identity.domain.event.AccountDeactivated;
import com.gudu.xsd.modules.identity.domain.event.AccountMerged;
import com.gudu.xsd.modules.identity.domain.event.AccountRegistered;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * family 上下文对 identity 事件与自身 MemberJoined 的同步消费（同事务，异常整体回滚）。
 *
 * <ul>
 *   <li>AccountRegistered：带有效邀请码 → 加入邀请家庭；否则自动建「我的家」（OWNER）；</li>
 *   <li>MemberJoined：为账号在该家庭建立关联成员档案；</li>
 *   <li>AccountDeactivated：退出全部家庭（有其他成员的家庭的 OWNER → 抛异常，注销回滚）；匿名化关联档案；</li>
 *   <li>AccountMerged：W 的成员关系转给 P（同家庭保留较高角色）；W 的档案改指 P，P 在该家庭已有档案则 W 的降为无账号档案。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FamilyAccountListener {

    private final FamilyAppService familyApp;
    private final FamilyRepository families;
    private final ProfileRepository profiles;
    private final AccountQueryApi accountQuery;
    private final DomainEventPublisher events;
    private final Clock clock;

    @EventListener
    public void onRegistered(AccountRegistered e) {
        String code = e.inviteCode();
        if (code != null && familyApp.preview(code).usable()) {
            familyApp.join(e.accountId(), code);
            return;
        }
        if (e.autoCreateFamily()) familyApp.createFamily(e.accountId(), Family.DEFAULT_NAME);
    }

    @EventListener
    public void onMemberJoined(MemberJoined e) {
        AccountView acc = accountQuery.find(e.accountId()).orElse(null);
        familyApp.ensureProfile(e.familyId(), e.accountId(), acc);
    }

    @EventListener
    public void onDeactivated(AccountDeactivated e) {
        LocalDateTime now = LocalDateTime.now(clock);
        for (Family f : families.findActiveByAccount(e.accountId())) {
            f.releaseDeactivatedAccount(e.accountId(), now);
            families.save(f);
            events.publishFrom(f);
        }
        for (FamilyMemberProfile p : profiles.findByAccount(e.accountId())) {
            p.anonymize();
            profiles.save(p);
        }
    }

    @EventListener
    public void onMerged(AccountMerged e) {
        Long w = e.sourceAccountId();
        Long p = e.targetAccountId();
        for (Family f : families.findActiveByAccount(w)) {
            if (f.reassignAccount(w, p)) {
                families.save(f);
                events.publishFrom(f);
            }
        }
        for (FamilyMemberProfile wp : profiles.findByAccount(w)) {
            Long familyId = wp.familyId().value();
            if (profiles.findByFamilyAndAccount(familyId, p).isPresent()) {
                wp.unlinkAccount();
            } else {
                wp.linkAccount(p);
            }
            profiles.save(wp);
        }
        log.info("账号合并：家庭成员关系与档案 {} → {}", w, p);
    }
}
