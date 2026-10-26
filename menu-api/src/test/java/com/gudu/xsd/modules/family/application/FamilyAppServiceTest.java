package com.gudu.xsd.modules.family.application;

import com.gudu.xsd.common.BizException;
import com.gudu.xsd.common.domain.DomainEventPublisher;
import com.gudu.xsd.modules.family.application.dto.FamilyViews.SessionFamily;
import com.gudu.xsd.modules.family.domain.event.FamilyCreated;
import com.gudu.xsd.modules.family.domain.event.MemberLeft;
import com.gudu.xsd.modules.family.domain.model.Family;
import com.gudu.xsd.modules.family.domain.model.FamilyMemberProfile;
import com.gudu.xsd.modules.family.domain.model.FamilyName;
import com.gudu.xsd.modules.family.domain.model.FamilyRole;
import com.gudu.xsd.modules.family.domain.model.Invitation;
import com.gudu.xsd.modules.family.domain.model.Membership;
import com.gudu.xsd.modules.family.domain.repository.FamilyRepository;
import com.gudu.xsd.modules.family.domain.repository.FamilyRoleCache;
import com.gudu.xsd.modules.family.domain.repository.ProfileRepository;
import com.gudu.xsd.common.tenant.FamilyId;
import com.gudu.xsd.modules.identity.application.AccountAppService;
import com.gudu.xsd.modules.identity.application.AccountQueryApi;
import com.gudu.xsd.modules.identity.application.dto.AccountView;
import com.gudu.xsd.modules.identity.domain.event.AccountMerged;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

/** 家庭应用层（B.6）：404 / 403 语义、MembershipPolicy 调用、会话解析与自动建家、事件发布、合并监听。 */
class FamilyAppServiceTest {

    static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 26, 12, 0);
    final FamilyRepository families = mock(FamilyRepository.class);
    final ProfileRepository profiles = mock(ProfileRepository.class);
    final AccountQueryApi accountQuery = mock(AccountQueryApi.class);
    final com.gudu.xsd.common.privacy.UploadedFileEraser files = mock(com.gudu.xsd.common.privacy.UploadedFileEraser.class);
    final AccountAppService accounts = mock(AccountAppService.class);
    final DomainEventPublisher events = mock(DomainEventPublisher.class, CALLS_REAL_METHODS);
    final Clock clock = Clock.fixed(Instant.parse("2026-10-26T04:00:00Z"), ZoneId.of("Asia/Shanghai"));
    FamilyAppService svc;

    static Family family(long id, long owner, long... members) {
        List<Membership> ms = new java.util.ArrayList<>();
        ms.add(new Membership(owner, FamilyRole.OWNER, NOW));
        for (long m : members) ms.add(new Membership(m, FamilyRole.MEMBER, NOW));
        return Family.rehydrate(id, new FamilyName("家" + id), null, ms, List.of(), NOW, null, 0);
    }

    @BeforeEach
    void setUp() {
        svc = new FamilyAppService(families, profiles, accountQuery, accounts, events, clock);
        when(accountQuery.find(any())).thenAnswer(i -> Optional.of(new AccountView(i.getArgument(0), "昵称", null,
                null, true, false, "USER", "ACTIVE")));
        doAnswer(i -> {
            Family f = i.getArgument(0);
            if (f.id() == null) f.identify(100L);
            return null;
        }).when(families).save(any());
        doAnswer(i -> {
            ((FamilyMemberProfile) i.getArgument(0)).identify(500L);
            return null;
        }).when(profiles).save(any());
    }

    @Test
    void 非成员访问家庭_404_不泄露存在性() {
        when(families.findById(10L)).thenReturn(Optional.of(family(10L, 1L, 2L)));
        assertThatThrownBy(() -> svc.detail(9L, 10L))
                .isInstanceOfSatisfying(BizException.class, e -> assertThat(e.getCode()).isEqualTo(404));
        assertThatThrownBy(() -> svc.switchTo(9L, 10L))
                .isInstanceOfSatisfying(BizException.class, e -> assertThat(e.getCode()).isEqualTo(404));
        assertThatThrownBy(() -> svc.dissolve(9L, 10L))
                .isInstanceOfSatisfying(BizException.class, e -> assertThat(e.getCode()).isEqualTo(404));
        verify(families, never()).save(any());
    }

    @Test
    void 角色不足_403() {
        when(families.findById(10L)).thenReturn(Optional.of(family(10L, 1L, 2L)));
        assertThatThrownBy(() -> svc.invite(2L, 10L))
                .isInstanceOfSatisfying(BizException.class, e -> assertThat(e.getCode()).isEqualTo(403));
        assertThatThrownBy(() -> svc.remove(2L, 10L, 1L))
                .isInstanceOfSatisfying(BizException.class, e -> assertThat(e.getCode()).isEqualTo(403));
    }

    @Test
    void 无家庭的账号登录_自动建我的家_成为OWNER_建档案_记住家庭() {
        when(families.findActiveByAccount(7L)).thenReturn(List.of());
        when(families.countActiveByAccount(7L)).thenReturn(0L);
        SessionFamily sf = svc.resolveSession(7L, null);
        assertThat(sf.familyId()).isEqualTo(100L);
        assertThat(sf.role()).isEqualTo("OWNER");
        assertThat(sf.profileId()).isEqualTo(500L);
        verify(events).publish(argThat(e -> e instanceof FamilyCreated));
        verify(accounts).rememberFamily(7L, 100L);
    }

    @Test
    void 会话解析_优先已选_其次上次使用_再次最早加入() {
        Family a = family(10L, 7L);
        Family b = family(11L, 8L, 7L);
        when(families.findActiveByAccount(7L)).thenReturn(List.of(a, b));
        when(profiles.findByFamilyAndAccount(any(), any())).thenReturn(Optional.of(
                FamilyMemberProfile.rehydrate(1L, FamilyId.of(10L), 7L, "x", null, null, null, null)));
        assertThat(svc.resolveSession(7L, 11L).familyId()).isEqualTo(11L);
        when(accounts.lastFamilyId(7L)).thenReturn(11L);
        assertThat(svc.resolveSession(7L, 99L).familyId()).as("已选家庭不再是成员 → 上次使用").isEqualTo(11L);
        when(accounts.lastFamilyId(7L)).thenReturn(null);
        assertThat(svc.resolveSession(7L, null).familyId()).isEqualTo(10L);
    }

    @Test
    void 加入_超过5个家庭拒绝_已在家庭拒绝() {
        Family f = family(10L, 1L);
        Invitation inv = f.invite(1L, NOW, null, 0);
        when(families.findByInviteCode(inv.code())).thenReturn(Optional.of(f));
        when(families.countActiveByAccount(7L)).thenReturn(5L);
        assertThatThrownBy(() -> svc.join(7L, inv.code().value())).hasMessageContaining("5");
        assertThatThrownBy(() -> svc.join(1L, inv.code().value())).hasMessageContaining("已在");
        verify(families, never()).save(any());
    }

    @Test
    void 合并监听_W的成员关系转给P_档案改指P_P已有档案则W的降为无账号档案() {
        FamilyAccountListener listener = new FamilyAccountListener(svc, families, profiles, accountQuery, files, events, clock);
        Family f1 = family(10L, 2L);           // W 是 F1 主理人
        Family f2 = family(11L, 1L, 2L);       // P 与 W 都在 F2
        when(families.findActiveByAccount(2L)).thenReturn(List.of(f1, f2));
        FamilyMemberProfile wp1 = FamilyMemberProfile.rehydrate(21L, FamilyId.of(10L), 2L, "W", null, null, null, null);
        FamilyMemberProfile wp2 = FamilyMemberProfile.rehydrate(22L, FamilyId.of(11L), 2L, "W", null, null, null, null);
        when(profiles.findByAccount(2L)).thenReturn(List.of(wp1, wp2));
        when(profiles.findByFamilyAndAccount(10L, 1L)).thenReturn(Optional.empty());
        when(profiles.findByFamilyAndAccount(11L, 1L)).thenReturn(Optional.of(
                FamilyMemberProfile.rehydrate(12L, FamilyId.of(11L), 1L, "P", null, null, null, null)));

        listener.onMerged(new AccountMerged(2L, 1L));

        assertThat(f1.roleOf(1L)).contains(FamilyRole.OWNER);
        assertThat(f2.roleOf(1L)).contains(FamilyRole.OWNER);
        assertThat(f2.isMember(2L)).isFalse();
        assertThat(wp1.accountId()).isEqualTo(1L);
        assertThat(wp2.accountId()).isNull();
        verify(events).publish(argThat(e -> e instanceof com.gudu.xsd.modules.family.domain.event.ProfileMerged m
                && m.fromProfileId() == 22L && m.toProfileId() == 12L));
        verify(events, atLeastOnce()).publish(argThat(e -> e instanceof MemberLeft));
    }

    @Test
    void 注销监听_匿名化前收集账号与档案头像并删文件_档案保留accountId供后续步骤使用() {
        FamilyAccountListener listener = new FamilyAccountListener(svc, families, profiles, accountQuery, files, events, clock);
        when(families.findActiveByAccount(5L)).thenReturn(List.of());
        FamilyMemberProfile p = FamilyMemberProfile.rehydrate(31L, FamilyId.of(10L), 5L, "阿美",
                "/gudu/uploads/original/p.png", null, null, null);
        when(profiles.findByAccount(5L)).thenReturn(List.of(p));
        java.util.List<String> erased = new java.util.ArrayList<>();
        when(files.erase(any())).thenAnswer(inv -> {
            erased.add(inv.getArgument(0));
            assertThat(p.avatarUrl()).as("文件在档案匿名化之前删除").isNotNull();
            return 1;
        });

        listener.onDeactivated(new com.gudu.xsd.modules.identity.domain.event.AccountDeactivated(5L,
                "/gudu/uploads/original/a.png"));

        assertThat(erased).containsExactly("/gudu/uploads/original/a.png", "/gudu/uploads/original/p.png");
        assertThat(p.deactivated()).isTrue();
        assertThat(p.avatarUrl()).isNull();
        assertThat(p.accountId()).isEqualTo(5L);
    }
}
