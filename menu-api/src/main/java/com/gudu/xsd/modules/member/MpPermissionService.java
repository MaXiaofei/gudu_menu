package com.gudu.xsd.modules.member;

import com.gudu.xsd.common.tenant.TenantContext;
import com.gudu.xsd.common.tenant.TenantContextHolder;
import com.gudu.xsd.modules.family.application.FamilyQueryApi;
import com.gudu.xsd.modules.identity.application.AccountQueryApi;
import com.gudu.xsd.modules.member.mapper.MemberMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 小程序功能权限矩阵服务。
 *
 * 轻量权限模型(不做重型 RBAC 表):角色标签(role)做默认模板 + 个人 mp_permissions 勾选微调。
 * resolvePermissions / hasPermission 是纯函数算法地基(参照 PantryService 纯函数范式),可单测,
 * 故测试用 new MpPermissionService(null),运行期由 Spring 注入 MemberMapper。
 *
 * 语义:personalPicks 与角色默认取【并集】—— 个人勾选只能放宽(增),不能收紧(减)。
 */
@Service
public class MpPermissionService {

    /** 角色 role 字典 id → 默认权限模板。与 V02__dict role 字典对齐:32=掌勺 33=备菜 34=普通成员。 */
    private static final Map<String, Set<String>> ROLE_DEFAULTS = Map.of(
            // 掌勺(chef):全权
            "32", Set.of(
                    "dish.create", "dish.edit",
                    "menu.plan", "menu.view",
                    "pantry.manage",
                    "shopping.generate",
                    "review.create",
                    "health.view",
                    "ai.use"),
            // 备菜(prep):备菜相关 + 看菜单
            "33", Set.of(
                    "menu.plan", "menu.view",
                    "pantry.manage",
                    "shopping.generate"),
            // 普通成员(member):只读点评 + 看菜单 + 排菜计划（家庭基础功能）
            "34", Set.of(
                    "review.create", "menu.view", "menu.plan")
    );

    /** 家庭普通成员（FamilyRole.MEMBER）的默认模板 = 普通成员(34)。 */
    static final String MEMBER_DEFAULT_TAGS = "34";

    private final MemberMapper memberMapper;
    private final AccountQueryApi accountQuery;
    private final FamilyQueryApi familyQuery;

    /** 纯函数单测用：不查库、不判平台 / 家庭角色。 */
    public MpPermissionService(MemberMapper memberMapper) {
        this(memberMapper, null, null);
    }

    @Autowired
    public MpPermissionService(MemberMapper memberMapper, AccountQueryApi accountQuery, FamilyQueryApi familyQuery) {
        this.memberMapper = memberMapper;
        this.accountQuery = accountQuery;
        this.familyQuery = familyQuery;
    }

    /**
     * 解析某成员(roleTags + 个人勾选)的最终功能权限集合。
     *
     * @param roleTags      角色标签,逗号分隔的 role 字典 id 串(如 "32,34");null/空走空默认
     * @param personalPicks 个人功能勾选;null/空时只用角色默认,否则与角色默认取并集
     */
    public Set<String> resolvePermissions(String roleTags, List<String> personalPicks) {
        Set<String> result = new HashSet<>();
        // 角色默认:多角色取并集(任一角色有的权限都给)
        if (roleTags != null && !roleTags.isBlank()) {
            Arrays.stream(roleTags.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(ROLE_DEFAULTS::get)
                    .filter(java.util.Objects::nonNull)
                    .forEach(result::addAll);
        }
        // 个人勾选并集(只能增不能减)
        if (personalPicks != null && !personalPicks.isEmpty()) {
            result.addAll(personalPicks);
        }
        return result;
    }

    /** 判定是否拥有某权限 key。 */
    public boolean hasPermission(String roleTags, List<String> personalPicks, String key) {
        return resolvePermissions(roleTags, personalPicks).contains(key);
    }

    /**
     * 运行期:本家某成员档案的最终权限集合(供前端按钮显隐)。E.4:
     * <ul>
     *   <li>档案关联的账号是平台超管,或在当前家庭是 OWNER/ADMIN → 全量;</li>
     *   <li>否则 = 普通成员模板(34) ∪ 档案角色标签模板 ∪ 档案级勾选;</li>
     *   <li>档案不在当前家庭(租户过滤查不到) → 空集。</li>
     * </ul>
     */
    public Set<String> resolveByMemberId(Long memberId) {
        if (memberId == null || memberMapper == null) {
            return Collections.emptySet();
        }
        Member m = memberMapper.selectById(memberId);
        if (m == null) {
            return Collections.emptySet();
        }
        if (m.getAccountId() != null && fullAccess(m.getAccountId())) {
            return new HashSet<>(allPermKeys());
        }
        return resolveProfile(m);
    }

    /**
     * 运行期:登录账号在当前家庭是否拥有某权限 key(@MpPerm 切面)。权限属于账号,与「当前就餐成员」无关。
     * 平台超管 / 家庭 OWNER、ADMIN 全权;普通成员按本人在当前家庭的档案解析。
     */
    public boolean hasPermission(Long accountId, String key) {
        if (accountId == null) return false;
        if (fullAccess(accountId)) return true;
        if (memberMapper == null) return false;
        Member own = memberMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Member>()
                .eq("account_id", accountId).last("LIMIT 1"));
        Set<String> perms = own == null ? resolvePermissions(MEMBER_DEFAULT_TAGS, null) : resolveProfile(own);
        return perms.contains(key);
    }

    private Set<String> resolveProfile(Member m) {
        Set<String> out = resolvePermissions(MEMBER_DEFAULT_TAGS, null);
        out.addAll(resolvePermissions(m.getRoleTags(), m.getMpPermissions()));
        return out;
    }

    /** 平台超管,或在当前家庭为 OWNER/ADMIN。 */
    private boolean fullAccess(Long accountId) {
        if (accountQuery != null && accountQuery.isSuperAdmin(accountId)) return true;
        Long familyId = TenantContextHolder.familyIdOrNull();
        if (familyQuery == null || familyId == null) return false;
        return familyQuery.roleOf(familyId, accountId)
                .map(r -> TenantContext.OWNER.equals(r) || TenantContext.ADMIN.equals(r)).orElse(false);
    }

    /** 全量功能 key 与中文映射(供前端/管理页展示)。 */
    public static Map<String, String> allPermLabels() {
        return Map.of(
                "dish.create", "录入菜品",
                "dish.edit", "编辑菜品",
                "menu.plan", "排菜计划",
                "menu.view", "查看菜单",
                "pantry.manage", "管理库存",
                "shopping.generate", "生成采购清单",
                "review.create", "发表点评",
                "health.view", "查看健康档案",
                "ai.use", "AI 助手");
    }

    /** 全量功能 key 列表(用于表单多选项)。 */
    public static List<String> allPermKeys() {
        return allPermLabels().entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }
}
