package com.gudu.xsd.modules.family.domain;

import com.gudu.xsd.common.domain.DomainEvent;
import com.gudu.xsd.common.domain.DomainException;
import com.gudu.xsd.modules.family.domain.event.FamilyCreated;
import com.gudu.xsd.modules.family.domain.event.FamilyDissolved;
import com.gudu.xsd.modules.family.domain.event.MemberJoined;
import com.gudu.xsd.modules.family.domain.event.MemberLeft;
import com.gudu.xsd.modules.family.domain.event.OwnershipTransferred;
import com.gudu.xsd.modules.family.domain.model.Family;
import com.gudu.xsd.modules.family.domain.model.FamilyName;
import com.gudu.xsd.modules.family.domain.model.FamilyRole;
import com.gudu.xsd.modules.family.domain.model.Invitation;
import com.gudu.xsd.modules.family.domain.model.InviteCode;
import com.gudu.xsd.modules.family.domain.model.Membership;
import com.gudu.xsd.modules.family.domain.service.MembershipPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Family 聚合（multi-tenant-family B.6）：不变量 I1–I4 全分支、邀请过期/用尽/撤销、角色矩阵（3 角色 × 6 行为）。
 * 纯 JUnit，不依赖 Spring。
 */
class FamilyTest {

    static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 26, 12, 0);
    static final long OWNER = 1L, ADMIN = 2L, MEMBER = 3L, OUTSIDER = 9L;

    /** id=10 的家庭：1=OWNER，2=ADMIN，3=MEMBER。 */
    static Family family() {
        return Family.rehydrate(10L, new FamilyName("咕嘟之家"), null, List.of(
                new Membership(OWNER, FamilyRole.OWNER, NOW),
                new Membership(ADMIN, FamilyRole.ADMIN, NOW),
                new Membership(MEMBER, FamilyRole.MEMBER, NOW)), List.of(), NOW, null, 0);
    }

    static long owners(Family f) {
        return f.memberships().stream().filter(m -> m.role() == FamilyRole.OWNER).count();
    }

    // ------------------------------------------------------------------ 创建与事件

    @Test
    void 建家_创建人是唯一OWNER_保存回填id后发FamilyCreated与MemberJoined() {
        Family f = Family.create(new FamilyName(" 我的家 "), 5L, NOW);
        assertThat(f.name().value()).isEqualTo("我的家");
        assertThat(f.roleOf(5L)).contains(FamilyRole.OWNER);
        assertThat(f.peekDomainEvents()).isEmpty();
        f.identify(77L);
        List<DomainEvent> events = f.pullDomainEvents();
        assertThat(events).hasSize(2);
        assertThat(events.get(0)).isInstanceOf(FamilyCreated.class);
        assertThat(((MemberJoined) events.get(1)).familyId()).isEqualTo(77L);
    }

    @Test
    void 家庭名_1到20字() {
        assertThatThrownBy(() -> new FamilyName(" ")).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new FamilyName("一".repeat(21))).isInstanceOf(DomainException.class);
        assertThatCode(() -> new FamilyName("一".repeat(20))).doesNotThrowAnyException();
    }

    // ------------------------------------------------------------------ 邀请与加入

    @Test
    void 邀请加入_成功_用量加一_发MemberJoined() {
        Family f = family();
        Invitation inv = f.invite(ADMIN, NOW, Duration.ofDays(7), 2);
        f.join(20L, inv.code(), NOW.plusDays(1));
        assertThat(f.roleOf(20L)).contains(FamilyRole.MEMBER);
        assertThat(f.invitation(inv.code()).orElseThrow().usedCount()).isEqualTo(1);
        assertThat(f.pullDomainEvents()).anyMatch(e -> e instanceof MemberJoined j && j.accountId() == 20L);
    }

    @Test
    void 邀请_过期_撤销_用尽_一律拒绝() {
        Family f = family();
        Invitation inv = f.invite(OWNER, NOW, Duration.ofDays(7), 1);
        assertThatThrownBy(() -> f.join(20L, inv.code(), NOW.plusDays(7))).hasMessageContaining("过期");
        f.join(20L, inv.code(), NOW);
        assertThatThrownBy(() -> f.join(21L, inv.code(), NOW)).hasMessageContaining("用完");
        Invitation inv2 = f.invite(OWNER, NOW, Duration.ofDays(7), 5);
        f.revokeInvitation(ADMIN, inv2.code());
        assertThatThrownBy(() -> f.join(22L, inv2.code(), NOW)).hasMessageContaining("失效");
        assertThatThrownBy(() -> f.join(23L, InviteCode.random(), NOW)).hasMessageContaining("无效");
    }

    @Test
    void I2_重复加入拒绝_不产生重复成员关系() {
        Family f = family();
        Invitation inv = f.invite(OWNER, NOW, null, 0);
        assertThatThrownBy(() -> f.join(MEMBER, inv.code(), NOW)).hasMessageContaining("已在该家庭中");
        assertThat(f.memberships()).hasSize(3);
        assertThat(f.invitation(inv.code()).orElseThrow().usedCount()).isZero();
    }

    @Test
    void I3_成员上限20人() {
        Family f = family();
        for (long i = 100; i < 117; i++) f.admit(i, NOW);
        assertThat(f.memberships()).hasSize(Family.MAX_MEMBERS);
        assertThatThrownBy(() -> f.admit(999L, NOW)).hasMessageContaining("已满");
    }

    @Test
    void 每账号最多5个家庭() {
        assertThatCode(() -> MembershipPolicy.ensureCanJoinAnother(4)).doesNotThrowAnyException();
        assertThatThrownBy(() -> MembershipPolicy.ensureCanJoinAnother(5)).hasMessageContaining("5");
    }

    @Test
    void 邀请码格式_忽略大小写() {
        InviteCode c = InviteCode.random();
        assertThat(InviteCode.parse(" " + c.value().toLowerCase() + " ")).isEqualTo(c);
        assertThatThrownBy(() -> InviteCode.parse("abc")).isInstanceOf(DomainException.class);
    }

    // ------------------------------------------------------------------ 角色矩阵：3 角色 × 6 行为

    /** 行为：invite / revoke / changeRole / remove(MEMBER) / transfer / dissolve；Y=允许 N=403。 */
    @ParameterizedTest(name = "{0} {1} → {2}")
    @CsvSource({
            "1, invite, Y", "2, invite, Y", "3, invite, N",
            "1, revoke, Y", "2, revoke, Y", "3, revoke, N",
            "1, changeRole, Y", "2, changeRole, N", "3, changeRole, N",
            "1, removeMember, Y", "2, removeMember, Y", "3, removeMember, N",
            "1, transfer, Y", "2, transfer, N", "3, transfer, N",
            "1, dissolve, Y", "2, dissolve, N", "3, dissolve, N",
            "9, invite, N", "9, dissolve, N"})
    void 角色矩阵(long operator, String action, String allowed) {
        Family f = family();
        Invitation inv = f.invite(OWNER, NOW, null, 0);
        Runnable r = switch (action) {
            case "invite" -> () -> f.invite(operator, NOW, null, 0);
            case "revoke" -> () -> f.revokeInvitation(operator, inv.code());
            case "changeRole" -> () -> f.changeRole(operator, MEMBER, FamilyRole.ADMIN);
            case "removeMember" -> () -> {
                f.admit(4L, NOW); // 另一位普通成员（避免与「移除自己」规则混淆）
                f.remove(operator, 4L);
            };
            case "transfer" -> () -> f.transferOwnership(operator, MEMBER);
            case "dissolve" -> () -> f.dissolve(operator, NOW);
            default -> throw new IllegalArgumentException(action);
        };
        if ("Y".equals(allowed)) {
            assertThatCode(r::run).doesNotThrowAnyException();
        } else {
            assertThatThrownBy(r::run).isInstanceOf(FamilyForbiddenException.class);
        }
        assertThat(owners(f)).as("I1 任何操作后恰好一个 OWNER（解散不改成员关系）").isEqualTo(1);
    }

    @Test
    void 移除_ADMIN不能移除ADMIN或OWNER_OWNER不能移除自己() {
        Family f = family();
        f.admit(4L, NOW);
        f.changeRole(OWNER, 4L, FamilyRole.ADMIN);
        assertThatThrownBy(() -> f.remove(ADMIN, 4L)).isInstanceOf(FamilyForbiddenException.class);
        assertThatThrownBy(() -> f.remove(ADMIN, OWNER)).isInstanceOf(FamilyForbiddenException.class);
        assertThatThrownBy(() -> f.remove(OWNER, OWNER)).hasMessageContaining("退出家庭");
        f.remove(OWNER, ADMIN);
        assertThat(f.isMember(ADMIN)).isFalse();
        assertThat(f.pullDomainEvents()).anyMatch(e -> e instanceof MemberLeft l && l.accountId() == ADMIN);
    }

    @Test
    void I1_不能借调整角色产生第二个OWNER() {
        Family f = family();
        assertThatThrownBy(() -> f.changeRole(OWNER, MEMBER, FamilyRole.OWNER)).hasMessageContaining("转让");
        assertThatThrownBy(() -> f.changeRole(OWNER, OWNER, FamilyRole.MEMBER)).isInstanceOf(DomainException.class);
        assertThat(owners(f)).isEqualTo(1);
    }

    // ------------------------------------------------------------------ 退出 / 转让 / 解散

    @Test
    void OWNER不能直接退出_成员可退出() {
        Family f = family();
        assertThatThrownBy(() -> f.leave(OWNER)).hasMessageContaining("转让");
        f.leave(MEMBER);
        assertThat(f.isMember(MEMBER)).isFalse();
        assertThatThrownBy(() -> f.leave(OUTSIDER)).hasMessageContaining("不在");
    }

    @Test
    void 转让_新主为OWNER_原主降为ADMIN_新主必须是成员() {
        Family f = family();
        assertThatThrownBy(() -> f.transferOwnership(OWNER, OUTSIDER)).hasMessageContaining("必须是家庭成员");
        f.transferOwnership(OWNER, MEMBER);
        assertThat(f.roleOf(MEMBER)).contains(FamilyRole.OWNER);
        assertThat(f.roleOf(OWNER)).contains(FamilyRole.ADMIN);
        assertThat(owners(f)).isEqualTo(1);
        assertThat(f.ownerAccountId()).isEqualTo(MEMBER);
        assertThat(f.pullDomainEvents()).anyMatch(e -> e instanceof OwnershipTransferred);
    }

    @Test
    void I4_解散后拒绝一切变更() {
        Family f = family();
        f.dissolve(OWNER, NOW);
        assertThat(f.isActive()).isFalse();
        assertThat(f.pullDomainEvents()).anyMatch(e -> e instanceof FamilyDissolved);
        assertThatThrownBy(() -> f.invite(OWNER, NOW, null, 0)).hasMessageContaining("解散");
        assertThatThrownBy(() -> f.admit(20L, NOW)).hasMessageContaining("解散");
        assertThatThrownBy(() -> f.leave(MEMBER)).hasMessageContaining("解散");
        assertThatThrownBy(() -> f.rename(OWNER, new FamilyName("新名"))).hasMessageContaining("解散");
        assertThatThrownBy(() -> f.dissolve(OWNER, NOW)).hasMessageContaining("解散");
    }

    // ------------------------------------------------------------------ 账号生命周期

    @Test
    void 注销_有其他成员的OWNER拒绝_独自OWNER解散_普通成员退出() {
        Family f = family();
        assertThatThrownBy(() -> f.releaseDeactivatedAccount(OWNER, NOW)).hasMessageContaining("先转让");
        f.releaseDeactivatedAccount(MEMBER, NOW);
        assertThat(f.isMember(MEMBER)).isFalse();

        Family solo = Family.create(new FamilyName("我的家"), 5L, NOW);
        solo.identify(11L);
        solo.releaseDeactivatedAccount(5L, NOW);
        assertThat(solo.isActive()).isFalse();
    }

    @Test
    void 合并_目标不在家庭则改指_已在则保留较高角色_OWNER随之转移() {
        Family f = family();
        assertThat(f.reassignAccount(OWNER, 50L)).isTrue();
        assertThat(f.roleOf(50L)).contains(FamilyRole.OWNER);
        assertThat(f.isMember(OWNER)).isFalse();

        Family g = family();
        assertThat(g.reassignAccount(OWNER, MEMBER)).isTrue(); // MEMBER 已在：取较高角色 OWNER
        assertThat(g.roleOf(MEMBER)).contains(FamilyRole.OWNER);
        assertThat(g.memberships()).hasSize(2);
        assertThat(owners(g)).isEqualTo(1);

        Family h = family();
        assertThat(h.reassignAccount(MEMBER, ADMIN)).isTrue(); // 来源角色低：目标保持 ADMIN
        assertThat(h.roleOf(ADMIN)).contains(FamilyRole.ADMIN);
        assertThat(h.reassignAccount(OUTSIDER, ADMIN)).isFalse();
    }
}
