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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
    private final com.gudu.xsd.common.privacy.UploadedFileEraser files;
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
        List<FamilyMemberProfile> mine = profiles.findByAccount(e.accountId());
        // 头像文件：地址在匿名化清空字段之前收集（账号头像来自事件，档案头像取自档案），逐个删除；
        // 失败只记日志，不影响注销（曾上传过的头像另由「头像文件删除」步骤按上传记录补删，后台可重试）
        Set<String> avatars = new LinkedHashSet<>();
        if (e.avatarUrl() != null && !e.avatarUrl().isBlank()) avatars.add(e.avatarUrl());
        for (FamilyMemberProfile p : mine) {
            if (p.avatarUrl() != null && !p.avatarUrl().isBlank()) avatars.add(p.avatarUrl());
        }
        for (String url : avatars) {
            try {
                files.erase(url);
            } catch (RuntimeException ex) {
                log.error("注销头像文件删除失败 account={} url={}: {}", e.accountId(), url, ex.getMessage());
            }
        }
        for (FamilyMemberProfile p : mine) {
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
            var target = profiles.findByFamilyAndAccount(familyId, p);
            if (target.isPresent()) {
                wp.mergeInto(target.get().id());
            } else {
                wp.linkAccount(p);
            }
            profiles.save(wp);
            events.publishFrom(wp);
        }
        log.info("账号合并：家庭成员关系与档案 {} → {}", w, p);
    }
}
