package com.gudu.xsd.common.tenant;

/**
 * 当前请求的租户上下文（multi-tenant-family design §4.1）。
 *
 * <ul>
 *   <li>{@code familyId}：当前家庭；游客为 null（只能读公共数据，家庭域表读为空、写被拒绝）。</li>
 *   <li>{@code accountId}：登录账号；游客 / 后台任务为 null。</li>
 *   <li>{@code profileId}：该账号在当前家庭的成员档案（member.id）——「谁在吃」的默认值。</li>
 *   <li>{@code familyRole}：OWNER / ADMIN / MEMBER（字符串，避免共享内核依赖 family 上下文）。</li>
 * </ul>
 */
public record TenantContext(Long familyId, Long accountId, Long profileId, String familyRole) {

    public static final String OWNER = "OWNER";
    public static final String ADMIN = "ADMIN";
    public static final String MEMBER = "MEMBER";

    /** 游客：无家庭、无账号。 */
    public static TenantContext guest() {
        return new TenantContext(null, null, null, null);
    }

    /** 后台任务 / 聚餐访客：以某个家庭身份运行（无账号）。 */
    public static TenantContext ofFamily(Long familyId) {
        return new TenantContext(familyId, null, null, null);
    }

    public boolean isGuest() {
        return familyId == null;
    }

    /** OWNER 或 ADMIN（家庭管理权限）。 */
    public boolean canManage() {
        return OWNER.equals(familyRole) || ADMIN.equals(familyRole);
    }

    public FamilyId requireFamily() {
        if (familyId == null) throw new TenantAccessDeniedException("请先登录并选择家庭");
        return FamilyId.of(familyId);
    }
}
