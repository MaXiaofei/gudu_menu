package com.gudu.xsd.modules.family.domain.model;

import com.gudu.xsd.common.domain.AggregateRoot;
import com.gudu.xsd.common.domain.DomainException;
import com.gudu.xsd.modules.family.domain.FamilyForbiddenException;
import com.gudu.xsd.modules.family.domain.event.FamilyCreated;
import com.gudu.xsd.modules.family.domain.event.FamilyDissolved;
import com.gudu.xsd.modules.family.domain.event.MemberJoined;
import com.gudu.xsd.modules.family.domain.event.MemberLeft;
import com.gudu.xsd.modules.family.domain.event.MemberRoleChanged;
import com.gudu.xsd.modules.family.domain.event.OwnershipTransferred;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 家庭聚合根（= 租户，design §3.2）。不变量：
 * <ul>
 *   <li>I1 恰好一个 OWNER；</li>
 *   <li>I2 同一 accountId 至多一条 membership；</li>
 *   <li>I3 成员数 ≤ 20；</li>
 *   <li>I4 DISSOLVED 后拒绝一切变更。</li>
 * </ul>
 * 行为的 operator 为发起人 accountId，权限判断在聚合内（角色不足抛 {@link FamilyForbiddenException}）。
 */
public final class Family extends AggregateRoot<Long> {

    public static final int MAX_MEMBERS = 20;
    public static final Duration DEFAULT_INVITE_TTL = Duration.ofDays(7);
    public static final int DEFAULT_INVITE_MAX_USES = 10;
    /** 新用户自动建家的默认名。 */
    public static final String DEFAULT_NAME = "我的家";

    private Long id;
    private FamilyName name;
    private FamilyStatus status;
    private final List<Membership> memberships;
    private final List<Invitation> invitations;
    private final LocalDateTime createdAt;
    private LocalDateTime dissolvedAt;
    private boolean pendingCreated;
    /** 乐观锁版本（并发加入 / 移除时防丢更新），由仓储维护。 */
    private long version;

    private Family(Long id, FamilyName name, FamilyStatus status, List<Membership> memberships,
                   List<Invitation> invitations, LocalDateTime createdAt, LocalDateTime dissolvedAt) {
        this.id = id;
        this.name = Objects.requireNonNull(name, "name");
        this.status = status == null ? FamilyStatus.ACTIVE : status;
        this.memberships = new ArrayList<>(memberships == null ? List.of() : memberships);
        this.invitations = new ArrayList<>(invitations == null ? List.of() : invitations);
        this.createdAt = createdAt;
        this.dissolvedAt = dissolvedAt;
    }

    /** 建家：创建人成为唯一 OWNER。id 由仓储保存后 {@link #identify} 回填，届时发 FamilyCreated + MemberJoined。 */
    public static Family create(FamilyName name, Long ownerAccountId, LocalDateTime now) {
        Objects.requireNonNull(ownerAccountId, "ownerAccountId");
        Family f = new Family(null, name, FamilyStatus.ACTIVE,
                List.of(new Membership(ownerAccountId, FamilyRole.OWNER, now)), List.of(), now, null);
        f.pendingCreated = true;
        return f;
    }

    public static Family rehydrate(Long id, FamilyName name, FamilyStatus status, List<Membership> memberships,
                                   List<Invitation> invitations, LocalDateTime createdAt, LocalDateTime dissolvedAt,
                                   long version) {
        if (id == null) throw new DomainException("家庭不存在");
        Family f = new Family(id, name, status, memberships, invitations, createdAt, dissolvedAt);
        f.version = version;
        return f;
    }

    public long version() {
        return version;
    }

    /** 仓储保存成功后推进版本。 */
    public void advanceVersion() {
        version++;
    }

    public void identify(Long newId) {
        if (this.id != null) return;
        this.id = Objects.requireNonNull(newId, "id");
        if (pendingCreated) {
            Long owner = ownerAccountId();
            registerEvent(new FamilyCreated(newId, owner));
            registerEvent(new MemberJoined(newId, owner));
            pendingCreated = false;
        }
    }

    // ------------------------------------------------------------------ 邀请与加入

    /** 生成邀请（仅 OWNER/ADMIN）。 */
    public Invitation invite(Long operator, LocalDateTime now, Duration ttl, int maxUses) {
        ensureActive();
        requireManager(operator);
        Invitation inv = new Invitation(InviteCode.random(), operator, now.plus(ttl == null ? DEFAULT_INVITE_TTL : ttl),
                maxUses <= 0 ? DEFAULT_INVITE_MAX_USES : maxUses, 0, false, now);
        invitations.add(inv);
        return inv;
    }

    /** 凭码加入：校验未过期 / 未撤销 / 未用尽 / I2 / I3。跨账号「≤5 个家庭」由 MembershipPolicy 在应用层先校验。 */
    public void join(Long accountId, InviteCode code, LocalDateTime now) {
        ensureActive();
        int idx = invitationIndex(code);
        if (idx < 0) throw new DomainException("邀请无效");
        Invitation inv = invitations.get(idx);
        inv.ensureUsable(now);
        admit(accountId, now);
        invitations.set(idx, inv.used());
    }

    /** 直接加入（不经邀请）：平台超管代建账号时加入当前家庭。 */
    public void admit(Long accountId, LocalDateTime now) {
        ensureActive();
        Objects.requireNonNull(accountId, "accountId");
        if (isMember(accountId)) throw new DomainException("已在该家庭中");
        if (memberships.size() >= MAX_MEMBERS) throw new DomainException("家庭成员已满（最多 " + MAX_MEMBERS + " 人）");
        memberships.add(new Membership(accountId, FamilyRole.MEMBER, now));
        registerEvent(new MemberJoined(id, accountId));
    }

    public void revokeInvitation(Long operator, InviteCode code) {
        ensureActive();
        requireManager(operator);
        int idx = invitationIndex(code);
        if (idx < 0) throw new DomainException("邀请不存在");
        invitations.set(idx, invitations.get(idx).revoke());
    }

    // ------------------------------------------------------------------ 角色与成员

    /** 调整角色（仅 OWNER）：只能在 ADMIN / MEMBER 间调整，不能借此产生第二个 OWNER（I1）。 */
    public void changeRole(Long operator, Long target, FamilyRole role) {
        ensureActive();
        requireRole(operator, FamilyRole.OWNER, "只有主理人可以调整角色");
        if (role == null || role == FamilyRole.OWNER) throw new DomainException("请使用「转让主理人」");
        if (Objects.equals(operator, target)) throw new DomainException("不能调整自己的角色");
        int idx = membershipIndex(target);
        if (idx < 0) throw new DomainException("该成员不在家庭中");
        memberships.set(idx, memberships.get(idx).withRole(role));
        registerEvent(new MemberRoleChanged(id, target, role.name()));
    }

    /** 移除成员：OWNER 可移除任何人（除自己）；ADMIN 只能移除 MEMBER。 */
    public void remove(Long operator, Long target) {
        ensureActive();
        FamilyRole opRole = roleOrThrow(operator);
        if (Objects.equals(operator, target)) throw new DomainException("不能移除自己，请使用「退出家庭」");
        int idx = membershipIndex(target);
        if (idx < 0) throw new DomainException("该成员不在家庭中");
        FamilyRole targetRole = memberships.get(idx).role();
        boolean allowed = opRole == FamilyRole.OWNER || (opRole == FamilyRole.ADMIN && targetRole == FamilyRole.MEMBER);
        if (!allowed) throw new FamilyForbiddenException("无权移除该成员");
        memberships.remove(idx);
        registerEvent(new MemberLeft(id, target));
    }

    /** 主动退出：OWNER 不可直接离开（须先转让或解散）。 */
    public void leave(Long accountId) {
        ensureActive();
        int idx = membershipIndex(accountId);
        if (idx < 0) throw new DomainException("你不在该家庭中");
        if (memberships.get(idx).role() == FamilyRole.OWNER) {
            throw new DomainException("主理人不能直接退出，请先转让主理人或解散家庭");
        }
        memberships.remove(idx);
        registerEvent(new MemberLeft(id, accountId));
    }

    /** 转让主理人（仅 OWNER）：新主须为成员；原 OWNER 降为 ADMIN。 */
    public void transferOwnership(Long operator, Long newOwner) {
        ensureActive();
        requireRole(operator, FamilyRole.OWNER, "只有主理人可以转让");
        if (Objects.equals(operator, newOwner)) throw new DomainException("你已经是主理人");
        int to = membershipIndex(newOwner);
        if (to < 0) throw new DomainException("新主理人必须是家庭成员");
        int from = membershipIndex(operator);
        memberships.set(from, memberships.get(from).withRole(FamilyRole.ADMIN));
        memberships.set(to, memberships.get(to).withRole(FamilyRole.OWNER));
        registerEvent(new OwnershipTransferred(id, operator, newOwner));
    }

    /** 解散（仅 OWNER）。家庭数据保留，成员关系保留以便审计，但家庭此后不可访问、不可变更。 */
    public void dissolve(Long operator, LocalDateTime now) {
        ensureActive();
        requireRole(operator, FamilyRole.OWNER, "只有主理人可以解散家庭");
        this.status = FamilyStatus.DISSOLVED;
        this.dissolvedAt = now;
        registerEvent(new FamilyDissolved(id));
    }

    public void rename(Long operator, FamilyName newName) {
        ensureActive();
        requireManager(operator);
        this.name = Objects.requireNonNull(newName, "name");
    }

    // ------------------------------------------------------------------ 账号生命周期（由 identity 事件驱动）

    /**
     * 账号注销：OWNER 且还有其他成员 → 拒绝（须先转让）；OWNER 且只剩自己 → 解散；其他角色 → 退出。
     */
    public void releaseDeactivatedAccount(Long accountId, LocalDateTime now) {
        if (status != FamilyStatus.ACTIVE) return;
        int idx = membershipIndex(accountId);
        if (idx < 0) return;
        if (memberships.get(idx).role() == FamilyRole.OWNER) {
            if (memberships.size() > 1) {
                throw new DomainException("你是「" + name.value() + "」的主理人，请先转让主理人或解散家庭");
            }
            dissolve(accountId, now);
            return;
        }
        memberships.remove(idx);
        registerEvent(new MemberLeft(id, accountId));
    }

    /**
     * 账号合并：把 from 的成员关系转给 to。to 已是成员时保留两者中较高的角色（OWNER 随之转移，I1 不破）；
     * 否则直接改指 to。已解散家庭也照常改指（只是归属变更，不算业务变更）。
     *
     * @return 是否发生变更
     */
    public boolean reassignAccount(Long from, Long to) {
        int fi = membershipIndex(from);
        if (fi < 0 || Objects.equals(from, to)) return false;
        Membership fm = memberships.get(fi);
        int ti = membershipIndex(to);
        if (ti < 0) {
            memberships.set(fi, fm.withAccount(to));
            registerEvent(new MemberLeft(id, from));
            return true;
        }
        Membership tm = memberships.get(ti);
        if (fm.role().higherThan(tm.role())) memberships.set(ti, tm.withRole(fm.role()));
        memberships.remove(fi);
        registerEvent(new MemberLeft(id, from));
        return true;
    }

    // ------------------------------------------------------------------ 查询

    public Optional<FamilyRole> roleOf(Long accountId) {
        int idx = membershipIndex(accountId);
        return idx < 0 ? Optional.empty() : Optional.of(memberships.get(idx).role());
    }

    public boolean isMember(Long accountId) {
        return membershipIndex(accountId) >= 0;
    }

    public boolean isActive() {
        return status == FamilyStatus.ACTIVE;
    }

    public Long ownerAccountId() {
        return memberships.stream().filter(m -> m.role() == FamilyRole.OWNER).map(Membership::accountId)
                .findFirst().orElse(null);
    }

    public Optional<Invitation> invitation(InviteCode code) {
        int idx = invitationIndex(code);
        return idx < 0 ? Optional.empty() : Optional.of(invitations.get(idx));
    }

    // ------------------------------------------------------------------ 规则

    private void ensureActive() {
        if (status != FamilyStatus.ACTIVE) throw new DomainException("家庭已解散");
    }

    private FamilyRole roleOrThrow(Long operator) {
        return roleOf(operator).orElseThrow(() -> new FamilyForbiddenException("你不是该家庭成员"));
    }

    private void requireManager(Long operator) {
        if (!roleOrThrow(operator).canManage()) throw new FamilyForbiddenException("只有主理人或管理员可以操作");
    }

    private void requireRole(Long operator, FamilyRole role, String msg) {
        if (roleOrThrow(operator) != role) throw new FamilyForbiddenException(msg);
    }

    private int membershipIndex(Long accountId) {
        for (int i = 0; i < memberships.size(); i++) {
            if (memberships.get(i).accountId().equals(accountId)) return i;
        }
        return -1;
    }

    private int invitationIndex(InviteCode code) {
        if (code == null) return -1;
        for (int i = 0; i < invitations.size(); i++) {
            if (invitations.get(i).code().equals(code)) return i;
        }
        return -1;
    }

    // ------------------------------------------------------------------ 只读

    @Override
    public Long id() {
        return id;
    }

    public FamilyName name() {
        return name;
    }

    public FamilyStatus status() {
        return status;
    }

    public List<Membership> memberships() {
        return Collections.unmodifiableList(memberships);
    }

    public List<Invitation> invitations() {
        return Collections.unmodifiableList(invitations);
    }

    public LocalDateTime createdAt() {
        return createdAt;
    }

    public LocalDateTime dissolvedAt() {
        return dissolvedAt;
    }
}
