package com.gudu.xsd.modules.family.application;

import com.gudu.xsd.common.domain.DomainEventPublisher;
import com.gudu.xsd.common.tenant.FamilyId;
import com.gudu.xsd.modules.family.domain.event.ProfileMerged;
import com.gudu.xsd.modules.family.domain.model.Family;
import com.gudu.xsd.modules.family.domain.model.FamilyMemberProfile;
import com.gudu.xsd.modules.family.domain.model.FamilyName;
import com.gudu.xsd.modules.family.domain.model.FamilyRole;
import com.gudu.xsd.modules.family.domain.model.FamilyStatus;
import com.gudu.xsd.modules.family.domain.model.Membership;
import com.gudu.xsd.modules.family.domain.repository.FamilyRepository;
import com.gudu.xsd.modules.family.domain.repository.ProfileReferences;
import com.gudu.xsd.modules.family.domain.repository.ProfileRepository;
import com.gudu.xsd.modules.identity.application.AccountQueryApi;
import com.gudu.xsd.modules.identity.domain.event.AccountMerged;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

/**
 * 账号合并后的清理（合并软删除）：重复档案软删除（先把按档案归属的数据改指目标档案）、
 * 「只剩 P 一人且无任何业务数据」的原微信账号私家庭软解散。W=2（微信）、P=1（手机号）。
 */
class AccountMergeCleanupTest {

    static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 2, 12, 0);
    static final long W = 2L;
    static final long P = 1L;
    final FamilyRepository families = mock(FamilyRepository.class);
    final ProfileRepository profiles = mock(ProfileRepository.class);
    final ProfileReferences refs = mock(ProfileReferences.class);
    final DomainEventPublisher events = mock(DomainEventPublisher.class, CALLS_REAL_METHODS);
    final Clock clock = Clock.fixed(Instant.parse("2026-11-02T04:00:00Z"), ZoneId.of("Asia/Shanghai"));
    FamilyAccountListener listener;

    static Family family(long id, long owner, long... members) {
        List<Membership> ms = new ArrayList<>();
        ms.add(new Membership(owner, FamilyRole.OWNER, NOW));
        for (long m : members) ms.add(new Membership(m, FamilyRole.MEMBER, NOW));
        return Family.rehydrate(id, new FamilyName("家" + id), null, ms, List.of(), NOW, null, 0);
    }

    static FamilyMemberProfile profile(long id, long familyId, Long accountId) {
        return FamilyMemberProfile.rehydrate(id, FamilyId.of(familyId), accountId, "n" + id, null, null, null, null);
    }

    @BeforeEach
    void setUp() {
        listener = new FamilyAccountListener(mock(FamilyAppService.class), families, profiles, refs,
                mock(AccountQueryApi.class), mock(com.gudu.xsd.common.privacy.UploadedFileEraser.class), events, clock);
        when(families.findActiveByAccount(P)).thenReturn(List.of());
    }

    // ------------------------------------------------------------------ 重复档案软删除

    @Test
    void P在同家庭已有档案_W档案数据改指P档案后软删除_且先改指后软删() {
        Family f = family(11L, P, W);
        when(families.findActiveByAccount(W)).thenReturn(List.of(f));
        FamilyMemberProfile wp = profile(22L, 11L, W);
        when(profiles.findByAccount(W)).thenReturn(List.of(wp));
        when(profiles.findByFamilyAndAccount(11L, P)).thenReturn(Optional.of(profile(12L, 11L, P)));

        listener.onMerged(new AccountMerged(W, P));

        InOrder order = inOrder(refs, profiles);
        order.verify(refs).reassign(22L, 12L);
        order.verify(profiles).softDelete(22L);
        verify(events).publish(argThat(e -> e instanceof ProfileMerged m && m.fromProfileId() == 22L && m.toProfileId() == 12L));
        assertThat(wp.accountId()).isNull();
    }

    @Test
    void P在该家庭没有档案_W档案直接改挂P_不软删不搬数据() {
        Family f = family(10L, W);
        when(families.findActiveByAccount(W)).thenReturn(List.of(f));
        FamilyMemberProfile wp = profile(21L, 10L, W);
        when(profiles.findByAccount(W)).thenReturn(List.of(wp));
        when(profiles.findByFamilyAndAccount(10L, P)).thenReturn(Optional.empty());

        listener.onMerged(new AccountMerged(W, P));

        assertThat(wp.accountId()).isEqualTo(P);
        verify(profiles, never()).softDelete(any());
        verify(refs, never()).reassign(any(), any());
    }

    @Test
    void 幂等_W已无家庭无档案_重复事件什么都不做() {
        when(families.findActiveByAccount(W)).thenReturn(List.of());
        when(profiles.findByAccount(W)).thenReturn(List.of());

        listener.onMerged(new AccountMerged(W, P));
        listener.onMerged(new AccountMerged(W, P));

        verify(profiles, never()).softDelete(any());
        verify(refs, never()).reassign(any(), any());
        verify(families, never()).save(any());
    }

    // ------------------------------------------------------------------ 空家庭软解散

    /** W 独自拥有的家庭 F10，合并后只剩 P；pHasOther 表示 P 另有 F20。 */
    private Family wOnlyFamily(boolean pHasOther) {
        Family f10 = family(10L, W);
        Family f20 = family(20L, P);
        when(families.findActiveByAccount(W)).thenReturn(List.of(f10));
        when(families.findActiveByAccount(P)).thenReturn(pHasOther ? List.of(f10, f20) : List.of(f10));
        FamilyMemberProfile wp = profile(21L, 10L, W);
        when(profiles.findByAccount(W)).thenReturn(List.of(wp));
        when(profiles.findByFamilyAndAccount(10L, P)).thenReturn(Optional.empty());
        // 合并后该家庭的档案：只有已改挂 P 的那一份
        when(profiles.findByFamily(10L)).thenReturn(List.of(profile(21L, 10L, P)));
        return f10;
    }

    @Test
    void 空家庭_只剩P一人且无业务数据_P另有家庭_软解散() {
        Family f10 = wOnlyFamily(true);
        when(refs.familyHasBusinessData(10L)).thenReturn(false);

        listener.onMerged(new AccountMerged(W, P));

        assertThat(f10.status()).isEqualTo(FamilyStatus.DISSOLVED);
        assertThat(f10.dissolvedAt()).isEqualTo(LocalDateTime.of(2026, 11, 2, 12, 0));
        verify(families, atLeastOnce()).save(f10);
    }

    @Test
    void 家庭有业务数据_保留() {
        Family f10 = wOnlyFamily(true);
        when(refs.familyHasBusinessData(10L)).thenReturn(true);

        listener.onMerged(new AccountMerged(W, P));

        assertThat(f10.status()).isEqualTo(FamilyStatus.ACTIVE);
        assertThat(f10.dissolvedAt()).isNull();
    }

    @Test
    void 家庭里有无账号档案_如宝宝_视为有数据_保留() {
        Family f10 = wOnlyFamily(true);
        when(refs.familyHasBusinessData(10L)).thenReturn(false);
        when(profiles.findByFamily(10L)).thenReturn(List.of(profile(21L, 10L, P), profile(23L, 10L, null)));

        listener.onMerged(new AccountMerged(W, P));

        assertThat(f10.status()).isEqualTo(FamilyStatus.ACTIVE);
    }

    @Test
    void P没有别的家庭_保留这个空家庭_避免P无家可归() {
        Family f10 = wOnlyFamily(false);
        when(refs.familyHasBusinessData(10L)).thenReturn(false);

        listener.onMerged(new AccountMerged(W, P));

        assertThat(f10.status()).isEqualTo(FamilyStatus.ACTIVE);
    }

    @Test
    void P合并前就在的家庭_即使W并入后只剩P且无数据_也不解散() {
        Family f11 = family(11L, P, W);       // P 与 W 同在
        Family f20 = family(20L, P);
        when(families.findActiveByAccount(W)).thenReturn(List.of(f11));
        when(families.findActiveByAccount(P)).thenReturn(List.of(f11, f20));
        FamilyMemberProfile wp = profile(22L, 11L, W);
        when(profiles.findByAccount(W)).thenReturn(List.of(wp));
        when(profiles.findByFamilyAndAccount(11L, P)).thenReturn(Optional.of(profile(12L, 11L, P)));
        when(profiles.findByFamily(11L)).thenReturn(List.of(profile(12L, 11L, P)));
        when(refs.familyHasBusinessData(11L)).thenReturn(false);

        listener.onMerged(new AccountMerged(W, P));

        assertThat(f11.status()).isEqualTo(FamilyStatus.ACTIVE);
        verify(refs, never()).familyHasBusinessData(11L);
    }
}
