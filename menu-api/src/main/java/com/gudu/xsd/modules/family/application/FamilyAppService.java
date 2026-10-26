package com.gudu.xsd.modules.family.application;

import com.gudu.xsd.common.BizException;
import com.gudu.xsd.common.domain.DomainEventPublisher;
import com.gudu.xsd.modules.family.application.dto.FamilyViews.FamilyDetail;
import com.gudu.xsd.modules.family.application.dto.FamilyViews.InvitationPreview;
import com.gudu.xsd.modules.family.application.dto.FamilyViews.InvitationView;
import com.gudu.xsd.modules.family.application.dto.FamilyViews.MemberView;
import com.gudu.xsd.modules.family.application.dto.FamilyViews.SessionFamily;
import com.gudu.xsd.modules.family.domain.FamilyForbiddenException;
import com.gudu.xsd.modules.family.domain.model.Family;
import com.gudu.xsd.modules.family.domain.model.FamilyMemberProfile;
import com.gudu.xsd.modules.family.domain.model.FamilyName;
import com.gudu.xsd.modules.family.domain.model.FamilyRole;
import com.gudu.xsd.modules.family.domain.model.Invitation;
import com.gudu.xsd.modules.family.domain.model.InviteCode;
import com.gudu.xsd.modules.family.domain.repository.FamilyRepository;
import com.gudu.xsd.modules.family.domain.repository.ProfileRepository;
import com.gudu.xsd.modules.family.domain.service.MembershipPolicy;
import com.gudu.xsd.common.domain.DomainException;
import com.gudu.xsd.common.tenant.FamilyId;
import com.gudu.xsd.modules.identity.application.AccountAppService;
import com.gudu.xsd.modules.identity.application.AccountQueryApi;
import com.gudu.xsd.modules.identity.application.dto.AccountView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 家庭用例（design §6 /family/**）。跨家庭访问统一 404（不泄露存在性），角色不足 403。
 * 应用服务薄：取聚合 → 调行为 → 存 → 发事件。
 */
@Service
@RequiredArgsConstructor
public class FamilyAppService {

    static final String NOT_FOUND = "家庭不存在";

    private final FamilyRepository families;
    private final ProfileRepository profiles;
    private final AccountQueryApi accountQuery;
    private final AccountAppService accounts;
    private final DomainEventPublisher events;
    private final Clock clock;

    // ------------------------------------------------------------------ 会话

    /**
     * 解析登录后的当前家庭：preferred（会话里已有）→ 上次使用 → 最早加入的家庭；一个都没有则自动建「我的家」。
     * 同时保证该账号在当前家庭有关联成员档案。未选家庭时落到唯一 / 默认家庭（兼容旧前端）。
     */
    @Transactional(rollbackFor = Exception.class)
    public SessionFamily resolveSession(Long accountId, Long preferredFamilyId) {
        AccountView acc = accountQuery.find(accountId).orElseThrow(() -> new BizException("账号不存在"));
        List<Family> mine = families.findActiveByAccount(accountId);
        Family chosen = pick(mine, preferredFamilyId);
        if (chosen == null) chosen = pick(mine, lastFamilyOf(accountId));
        if (chosen == null && !mine.isEmpty()) chosen = mine.get(0);
        if (chosen == null) chosen = createFamily(accountId, Family.DEFAULT_NAME);
        return bind(chosen, accountId, acc);
    }

    /** 切换当前家庭：必须是该（未解散）家庭成员，否则 404、当前家庭不变。 */
    @Transactional(rollbackFor = Exception.class)
    public SessionFamily switchTo(Long accountId, Long familyId) {
        Family f = loadAsMember(familyId, accountId);
        AccountView acc = accountQuery.find(accountId).orElseThrow(() -> new BizException("账号不存在"));
        return bind(f, accountId, acc);
    }

    private SessionFamily bind(Family f, Long accountId, AccountView acc) {
        Long profileId = ensureProfile(f.id(), accountId, acc).id();
        accounts.rememberFamily(accountId, f.id());
        return new SessionFamily(f.id(), profileId, f.roleOf(accountId).map(Enum::name).orElse(null));
    }

    private Long lastFamilyOf(Long accountId) {
        return accounts.lastFamilyId(accountId);
    }

    private static Family pick(List<Family> mine, Long familyId) {
        if (familyId == null) return null;
        return mine.stream().filter(f -> familyId.equals(f.id())).findFirst().orElse(null);
    }

    /** 保证账号在家庭内有关联档案（MemberJoined 监听已建；存量 / 异常情况兜底补建）。 */
    FamilyMemberProfile ensureProfile(Long familyId, Long accountId, AccountView acc) {
        return profiles.findByFamilyAndAccount(familyId, accountId).orElseGet(() -> {
            FamilyMemberProfile p = FamilyMemberProfile.forAccount(FamilyId.of(familyId), accountId,
                    acc == null ? null : acc.nickname(), acc == null ? null : acc.avatarUrl());
            profiles.save(p);
            return p;
        });
    }

    // ------------------------------------------------------------------ 家庭 CRUD

    @Transactional(rollbackFor = Exception.class)
    public Long create(Long accountId, String name) {
        return createFamily(accountId, name == null || name.isBlank() ? Family.DEFAULT_NAME : name).id();
    }

    Family createFamily(Long accountId, String name) {
        MembershipPolicy.ensureCanJoinAnother(families.countActiveByAccount(accountId));
        Family f = Family.create(new FamilyName(name), accountId, LocalDateTime.now(clock));
        families.save(f);
        events.publishFrom(f);
        return f;
    }

    public FamilyDetail detail(Long accountId, Long familyId) {
        Family f = loadAsMember(familyId, accountId);
        FamilyRole myRole = f.roleOf(accountId).orElseThrow();
        Map<Long, String> names = accountQuery.nicknames(f.memberships().stream().map(m -> m.accountId()).toList());
        List<MemberView> members = f.memberships().stream().map(m -> new MemberView(m.accountId(),
                names.getOrDefault(m.accountId(), ""), m.role().name(), m.joinedAt(),
                profiles.findByFamilyAndAccount(f.id(), m.accountId()).map(FamilyMemberProfile::id).orElse(null)))
                .toList();
        LocalDateTime now = LocalDateTime.now(clock);
        List<InvitationView> invs = !myRole.canManage() ? List.of() : f.invitations().stream()
                .filter(i -> !i.revoked() && now.isBefore(i.expiresAt()) && i.usedCount() < i.maxUses())
                .map(FamilyAppService::view).toList();
        return new FamilyDetail(f.id(), f.name().value(), f.status().name(), myRole.name(), members, invs);
    }

    @Transactional(rollbackFor = Exception.class)
    public void rename(Long accountId, Long familyId, String name) {
        mutate(familyId, accountId, f -> f.rename(accountId, new FamilyName(name)));
    }

    // ------------------------------------------------------------------ 邀请

    @Transactional(rollbackFor = Exception.class)
    public InvitationView invite(Long accountId, Long familyId) {
        Family f = loadAsMember(familyId, accountId);
        Invitation inv = guard(() -> f.invite(accountId, LocalDateTime.now(clock), Family.DEFAULT_INVITE_TTL,
                Family.DEFAULT_INVITE_MAX_USES));
        families.save(f);
        events.publishFrom(f);
        return view(inv);
    }

    @Transactional(rollbackFor = Exception.class)
    public void revokeInvitation(Long accountId, Long familyId, String code) {
        mutate(familyId, accountId, f -> f.revokeInvitation(accountId, InviteCode.parse(code)));
    }

    /** 游客可调用：只返回家庭名 / 邀请人昵称 / 是否可用，不含成员信息。 */
    public InvitationPreview preview(String rawCode) {
        InviteCode code = parseOrNull(rawCode);
        Family f = code == null ? null : families.findByInviteCode(code).orElse(null);
        if (f == null) return new InvitationPreview(null, null, null, false, "邀请无效");
        Invitation inv = f.invitation(code).orElseThrow();
        String reason = null;
        try {
            if (!f.isActive()) throw new DomainException("家庭已解散");
            inv.ensureUsable(LocalDateTime.now(clock));
        } catch (DomainException e) {
            reason = e.getMessage();
        }
        String inviter = accountQuery.nicknames(List.of(inv.createdBy())).getOrDefault(inv.createdBy(), "");
        return new InvitationPreview(f.name().value(), inviter, inv.expiresAt(), reason == null, reason);
    }

    /** 凭邀请码加入；成功后当前家庭切到被邀请的家庭。 */
    @Transactional(rollbackFor = Exception.class)
    public SessionFamily join(Long accountId, String rawCode) {
        InviteCode code = InviteCode.parse(rawCode);
        Family f = families.findByInviteCode(code).orElseThrow(() -> new DomainException("邀请无效"));
        if (f.isMember(accountId)) throw new DomainException("已在该家庭中");
        MembershipPolicy.ensureCanJoinAnother(families.countActiveByAccount(accountId));
        f.join(accountId, code, LocalDateTime.now(clock));
        families.save(f);
        events.publishFrom(f);
        AccountView acc = accountQuery.find(accountId).orElse(null);
        return bind(f, accountId, acc);
    }

    /**
     * 平台超管代建手机号账号：familyIdOrNull 非空 → 直接加入该家庭（MEMBER，不建「我的家」）；
     * 否则新账号自动建「我的家」。返回账号 id。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long registerPhoneAccount(String phone, String rawPassword, String nickname, Long familyIdOrNull) {
        Long id = accounts.registerByPhone(phone, rawPassword, nickname, familyIdOrNull == null);
        if (familyIdOrNull != null) admit(familyIdOrNull, id);
        return id;
    }

    /** 平台超管代建账号后直接加入指定家庭（不经邀请）。 */
    @Transactional(rollbackFor = Exception.class)
    public void admit(Long familyId, Long accountId) {
        Family f = families.findById(familyId).filter(Family::isActive)
                .orElseThrow(() -> BizException.notFound(NOT_FOUND));
        MembershipPolicy.ensureCanJoinAnother(families.countActiveByAccount(accountId));
        f.admit(accountId, LocalDateTime.now(clock));
        families.save(f);
        events.publishFrom(f);
    }

    // ------------------------------------------------------------------ 成员管理

    @Transactional(rollbackFor = Exception.class)
    public void changeRole(Long accountId, Long familyId, Long target, String role) {
        FamilyRole r;
        try {
            r = FamilyRole.of(role);
        } catch (RuntimeException e) {
            throw new BizException("角色无效");
        }
        mutate(familyId, accountId, f -> f.changeRole(accountId, target, r));
    }

    @Transactional(rollbackFor = Exception.class)
    public void remove(Long accountId, Long familyId, Long target) {
        mutate(familyId, accountId, f -> f.remove(accountId, target));
    }

    @Transactional(rollbackFor = Exception.class)
    public void leave(Long accountId, Long familyId) {
        mutate(familyId, accountId, f -> f.leave(accountId));
    }

    @Transactional(rollbackFor = Exception.class)
    public void transfer(Long accountId, Long familyId, Long newOwner) {
        mutate(familyId, accountId, f -> f.transferOwnership(accountId, newOwner));
    }

    @Transactional(rollbackFor = Exception.class)
    public void dissolve(Long accountId, Long familyId) {
        mutate(familyId, accountId, f -> f.dissolve(accountId, LocalDateTime.now(clock)));
    }

    // ------------------------------------------------------------------ 内部

    private void mutate(Long familyId, Long accountId, java.util.function.Consumer<Family> action) {
        Family f = loadAsMember(familyId, accountId);
        guard(() -> {
            action.accept(f);
            return null;
        });
        families.save(f);
        events.publishFrom(f);
    }

    /** 非成员 / 已解散 / 不存在一律 404。 */
    Family loadAsMember(Long familyId, Long accountId) {
        Family f = families.findById(familyId).orElseThrow(() -> BizException.notFound(NOT_FOUND));
        if (!f.isActive() || !f.isMember(accountId)) throw BizException.notFound(NOT_FOUND);
        return f;
    }

    /** 角色不足（领域 FamilyForbiddenException）→ 403。 */
    static <T> T guard(Supplier<T> body) {
        try {
            return body.get();
        } catch (FamilyForbiddenException e) {
            throw BizException.forbidden(e.getMessage());
        }
    }

    private static InviteCode parseOrNull(String raw) {
        try {
            return InviteCode.parse(raw);
        } catch (DomainException e) {
            return null;
        }
    }

    static InvitationView view(Invitation i) {
        return new InvitationView(i.code().value(), i.expiresAt(), i.maxUses(), i.usedCount(), i.revoked());
    }
}
