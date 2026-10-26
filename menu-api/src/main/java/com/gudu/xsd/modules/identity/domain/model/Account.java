package com.gudu.xsd.modules.identity.domain.model;

import com.gudu.xsd.common.domain.AggregateRoot;
import com.gudu.xsd.common.domain.DomainException;
import com.gudu.xsd.modules.identity.domain.AccountWechatConflictException;
import com.gudu.xsd.modules.identity.domain.event.AccountDeactivated;
import com.gudu.xsd.modules.identity.domain.event.AccountMerged;
import com.gudu.xsd.modules.identity.domain.event.AccountRegistered;
import com.gudu.xsd.modules.identity.domain.service.PasswordHasher;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 账号聚合根（identity 上下文，design §3.1）：能登录的人 = 凭证（手机号+密码 / 微信）+ 平台角色 + 状态。
 * 与「家庭成员档案」分离：一个账号可属于多个家庭，宝宝等无账号的人只有档案。
 */
public final class Account extends AggregateRoot<Long> {

    /** 微信静默注册的默认昵称；合并时视为「用户没改过」。 */
    public static final String DEFAULT_WECHAT_NICKNAME = "微信用户";
    static final int NICKNAME_MAX = 20;
    static final int PASSWORD_MIN = 6;
    static final int PASSWORD_MAX = 64;

    private Long id;
    private PhoneNumber phone;
    private WechatIdentity wechat;
    private String passwordHash;
    private PlatformRole platformRole;
    private AccountStatus status;
    private String nickname;
    private String avatarUrl;
    private Long lastFamilyId;
    private Long mergedInto;
    private final LocalDateTime createdAt;
    private LocalDateTime deactivatedAt;
    /** 新注册待分配 id 时暂存，identify 后发 AccountRegistered。 */
    private String pendingInviteCode;
    private boolean pendingRegistered;
    private boolean pendingAutoFamily = true;

    private Account(Long id, PhoneNumber phone, WechatIdentity wechat, String passwordHash, PlatformRole platformRole,
                    AccountStatus status, String nickname, String avatarUrl, Long lastFamilyId, Long mergedInto,
                    LocalDateTime createdAt, LocalDateTime deactivatedAt) {
        this.id = id;
        this.phone = phone;
        this.wechat = wechat;
        this.passwordHash = passwordHash;
        this.platformRole = platformRole == null ? PlatformRole.USER : platformRole;
        this.status = status == null ? AccountStatus.ACTIVE : status;
        this.nickname = nickname;
        this.avatarUrl = avatarUrl;
        this.lastFamilyId = lastFamilyId;
        this.mergedInto = mergedInto;
        this.createdAt = createdAt;
        this.deactivatedAt = deactivatedAt;
    }

    // ------------------------------------------------------------------ 工厂

    /** 微信静默注册：新 openid 首次登录。inviteCode 非空时由 family 上下文加入邀请家庭（否则自动建家）。 */
    public static Account registerByWechat(WechatIdentity wechat, String inviteCode, LocalDateTime now) {
        Objects.requireNonNull(wechat, "wechat");
        Account a = new Account(null, null, wechat, null, PlatformRole.USER, AccountStatus.ACTIVE,
                DEFAULT_WECHAT_NICKNAME, null, null, null, now, null);
        a.pendingRegistered = true;
        a.pendingInviteCode = inviteCode;
        return a;
    }

    /**
     * 手机号注册（V1 仅平台超管代建；短信通道接入后开放自助注册）。
     *
     * @param autoCreateFamily false = 调用方随后把账号加入指定家庭，不自动建「我的家」
     */
    public static Account registerByPhone(PhoneNumber phone, String rawPassword, String nickname,
                                          boolean autoCreateFamily, PasswordHasher hasher, LocalDateTime now) {
        Objects.requireNonNull(phone, "phone");
        Account a = new Account(null, phone, null, hasher.hash(checkPassword(rawPassword)), PlatformRole.USER,
                AccountStatus.ACTIVE, checkNickname(nickname == null || nickname.isBlank() ? "用户" + tail(phone) : nickname),
                null, null, null, now, null);
        a.pendingRegistered = true;
        a.pendingAutoFamily = autoCreateFamily;
        return a;
    }

    public static Account rehydrate(Long id, PhoneNumber phone, WechatIdentity wechat, String passwordHash,
                                    PlatformRole role, AccountStatus status, String nickname, String avatarUrl,
                                    Long lastFamilyId, Long mergedInto, LocalDateTime createdAt,
                                    LocalDateTime deactivatedAt) {
        if (id == null) throw new DomainException("账号不存在");
        return new Account(id, phone, wechat, passwordHash, role, status, nickname, avatarUrl, lastFamilyId,
                mergedInto, createdAt, deactivatedAt);
    }

    /** 仓储保存新账号后回填 id；新注册的账号此时登记 AccountRegistered。 */
    public void identify(Long newId) {
        if (this.id != null) return;
        if (newId == null) throw new DomainException("账号保存失败");
        this.id = newId;
        if (pendingRegistered) {
            registerEvent(new AccountRegistered(newId, nickname, pendingInviteCode, pendingAutoFamily));
            pendingRegistered = false;
            pendingInviteCode = null;
        }
    }

    // ------------------------------------------------------------------ 行为

    /** 可登录校验：注销 / 已合并账号拒绝登录。 */
    public void ensureCanLogin() {
        if (status != AccountStatus.ACTIVE) throw new DomainException("账号不可用");
    }

    /** 密码校验（无密码的微信账号恒失败）。 */
    public boolean passwordMatches(String raw, PasswordHasher hasher) {
        return raw != null && passwordHash != null && hasher.matches(raw, passwordHash);
    }

    public void changePassword(String oldRaw, String newRaw, PasswordHasher hasher) {
        ensureActive();
        if (passwordHash != null && !passwordMatches(oldRaw, hasher)) throw new DomainException("原密码不正确");
        this.passwordHash = hasher.hash(checkPassword(newRaw));
    }

    /** 平台超管重置密码（不校验旧密码；应用层校验操作人）。 */
    public void resetPassword(String newRaw, PasswordHasher hasher) {
        ensureActive();
        this.passwordHash = hasher.hash(checkPassword(newRaw));
    }

    public void bindPhone(PhoneNumber newPhone) {
        ensureActive();
        if (phone != null) throw new DomainException("已绑定手机号");
        this.phone = Objects.requireNonNull(newPhone, "phone");
    }

    /** 授予平台角色（仅 SUPER_ADMIN 可调用，操作人校验在应用层）。 */
    public void grantPlatformRole(PlatformRole role) {
        ensureActive();
        this.platformRole = Objects.requireNonNull(role, "role");
    }

    public void rename(String newNickname) {
        ensureActive();
        this.nickname = checkNickname(newNickname);
    }

    /** 头像：调用方已校验为本站上传地址；null/空串清除。 */
    public void changeAvatar(String url) {
        ensureActive();
        this.avatarUrl = url == null || url.isBlank() ? null : url.trim();
    }

    public void rememberFamily(Long familyId) {
        this.lastFamilyId = familyId;
    }

    /**
     * 注销：匿名化（清手机号 / 微信身份 / 密码 / 昵称 / 头像），status=DEACTIVATED。
     * 家庭侧（OWNER 须先转让或解散）由应用层在调用前校验。
     */
    public void deactivate(LocalDateTime now) {
        ensureActive();
        String oldAvatar = this.avatarUrl;
        this.phone = null;
        this.wechat = null;
        this.passwordHash = null;
        this.nickname = null;
        this.avatarUrl = null;
        this.lastFamilyId = null;
        this.status = AccountStatus.DEACTIVATED;
        this.deactivatedAt = now;
        registerEvent(new AccountDeactivated(id, oldAvatar));
    }

    /**
     * 账号合并（本账号 = 手机号账号 P 为主，source = 当前登录的微信账号 W）。
     *
     * @return true 本次完成合并；false 已经合并过（幂等，不重复发事件）
     */
    public boolean absorb(Account source, LocalDateTime now) {
        Objects.requireNonNull(source, "source");
        if (Objects.equals(id, source.id)) throw new DomainException("不能与自己合并");
        if (source.status == AccountStatus.MERGED && Objects.equals(source.mergedInto, id)) return false;
        ensureActive();
        if (source.status != AccountStatus.ACTIVE) throw new DomainException("当前账号不可合并");
        if (source.wechat == null) throw new DomainException("当前账号不是微信账号，无需合并");
        if (source.phone != null) throw new DomainException("当前账号已绑定手机号，无需合并");
        if (phone == null) throw new DomainException("目标账号未绑定手机号");
        if (wechat != null && !wechat.openid().equals(source.wechat.openid())) {
            throw new AccountWechatConflictException();
        }
        this.wechat = source.wechat;
        if (isDefaultNickname(nickname) && !isDefaultNickname(source.nickname)) this.nickname = source.nickname;
        if ((avatarUrl == null || avatarUrl.isBlank()) && source.avatarUrl != null) this.avatarUrl = source.avatarUrl;
        source.wechat = null;
        source.status = AccountStatus.MERGED;
        source.mergedInto = id;
        source.deactivatedAt = now;
        registerEvent(new AccountMerged(source.id, id));
        return true;
    }

    // ------------------------------------------------------------------ 规则

    private void ensureActive() {
        if (status != AccountStatus.ACTIVE) throw new DomainException("账号不可用");
    }

    static boolean isDefaultNickname(String n) {
        return n == null || n.isBlank() || DEFAULT_WECHAT_NICKNAME.equals(n.trim());
    }

    private static String checkPassword(String raw) {
        if (raw == null || raw.length() < PASSWORD_MIN || raw.length() > PASSWORD_MAX) {
            throw new DomainException("密码需 6–64 位");
        }
        return raw;
    }

    private static String checkNickname(String n) {
        String v = n == null ? "" : n.trim();
        if (v.isEmpty() || v.length() > NICKNAME_MAX) throw new DomainException("昵称需 1–20 个字");
        return v;
    }

    private static String tail(PhoneNumber p) {
        String v = p.value();
        return v.length() <= 4 ? v : v.substring(v.length() - 4);
    }

    // ------------------------------------------------------------------ 只读

    @Override
    public Long id() {
        return id;
    }

    public PhoneNumber phone() {
        return phone;
    }

    public WechatIdentity wechat() {
        return wechat;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public PlatformRole platformRole() {
        return platformRole;
    }

    public AccountStatus status() {
        return status;
    }

    public String nickname() {
        return nickname;
    }

    public String avatarUrl() {
        return avatarUrl;
    }

    public Long lastFamilyId() {
        return lastFamilyId;
    }

    public Long mergedInto() {
        return mergedInto;
    }

    public LocalDateTime createdAt() {
        return createdAt;
    }

    public LocalDateTime deactivatedAt() {
        return deactivatedAt;
    }

    public boolean isWechatOnly() {
        return wechat != null && phone == null;
    }
}
