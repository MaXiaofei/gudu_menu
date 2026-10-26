package com.gudu.xsd.modules.family.domain.model;

import com.gudu.xsd.common.domain.AggregateRoot;
import com.gudu.xsd.common.domain.DomainException;
import com.gudu.xsd.common.tenant.FamilyId;

import java.util.Objects;

/**
 * 成员档案聚合（design §3.3）：一起吃饭的人的健康 / 口味档案，可关联账号（同一家庭内一一对应），也可无账号（宝宝、老人）。
 * 存于 member 表（保留表名减少改动）。与 Family 聚合分开：档案编辑频繁，与成员关系无强一致约束。
 */
public final class FamilyMemberProfile extends AggregateRoot<Long> {

    static final int NAME_MAX = 32;
    public static final String DEACTIVATED_NAME = "已注销成员";

    private Long id;
    private final FamilyId familyId;
    private Long accountId;
    private String displayName;
    private String avatarUrl;
    private HealthProfile health;
    private String goal;
    private String activityLevel;

    private FamilyMemberProfile(Long id, FamilyId familyId, Long accountId, String displayName, String avatarUrl,
                                HealthProfile health, String goal, String activityLevel) {
        this.id = id;
        this.familyId = Objects.requireNonNull(familyId, "familyId");
        this.accountId = accountId;
        this.displayName = displayName;
        this.avatarUrl = avatarUrl;
        this.health = health;
        this.goal = goal;
        this.activityLevel = activityLevel;
    }

    /** 账号加入家庭时自动建立的关联档案（健康档案留空，引导用户填写）。 */
    public static FamilyMemberProfile forAccount(FamilyId familyId, Long accountId, String nickname, String avatarUrl) {
        Objects.requireNonNull(accountId, "accountId");
        return new FamilyMemberProfile(null, familyId, accountId, checkName(nickname == null || nickname.isBlank()
                ? "家庭成员" : nickname), avatarUrl, null, null, null);
    }

    public static FamilyMemberProfile rehydrate(Long id, FamilyId familyId, Long accountId, String displayName,
                                                String avatarUrl, HealthProfile health, String goal,
                                                String activityLevel) {
        if (id == null) throw new DomainException("成员不存在");
        return new FamilyMemberProfile(id, familyId, accountId, displayName, avatarUrl, health, goal, activityLevel);
    }

    public void identify(Long newId) {
        if (this.id == null) this.id = newId;
    }

    public void linkAccount(Long newAccountId) {
        this.accountId = Objects.requireNonNull(newAccountId, "accountId");
    }

    /** 解除账号关联（合并时同家庭已有目标账号档案 → 本档案降为无账号档案，数据不删）。 */
    public void unlinkAccount() {
        this.accountId = null;
    }

    /** 账号注销：匿名化（清名字 / 头像 / 健康档案），解除账号关联。 */
    public void anonymize() {
        this.accountId = null;
        this.displayName = DEACTIVATED_NAME;
        this.avatarUrl = null;
        this.health = null;
        this.goal = null;
        this.activityLevel = null;
    }

    public void updateHealth(HealthProfile newHealth, String newGoal, String newActivityLevel) {
        this.health = newHealth;
        this.goal = newGoal;
        this.activityLevel = newActivityLevel;
    }

    private static String checkName(String n) {
        String v = n == null ? "" : n.trim();
        if (v.isEmpty() || v.length() > NAME_MAX) throw new DomainException("名字需 1–32 个字");
        return v;
    }

    @Override
    public Long id() {
        return id;
    }

    public FamilyId familyId() {
        return familyId;
    }

    public Long accountId() {
        return accountId;
    }

    public String displayName() {
        return displayName;
    }

    public String avatarUrl() {
        return avatarUrl;
    }

    public HealthProfile health() {
        return health;
    }

    public String goal() {
        return goal;
    }

    public String activityLevel() {
        return activityLevel;
    }
}
