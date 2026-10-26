package com.gudu.xsd.common.tenant;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gudu.xsd.common.security.TenantSession;

/**
 * 菜谱可见性（design §4.3，dish 不进租户插件，手写）：公共菜谱（family_id IS NULL）+ 当前家庭私有菜谱。
 * 游客 / 无家庭上下文只看公共菜谱；系统模式不加条件。
 *
 * <p>管理后台登录（scope=admin）的平台运营是「公共菜谱库」视角：只看得到、只改得了公共菜谱（family_id IS NULL），
 * 即使该运营账号自己也有家庭，其家庭私有菜谱在后台菜谱库里不可见、不可写（家庭私有数据不进后台）。
 * 凡按 id 或条件读取 dish 的代码都必须套用本条件（ArchUnit / 越权 E2E 守护）。
 */
public final class DishVisibility {

    public static final String COLUMN = "family_id";

    private DishVisibility() {
    }

    /** 可见性判定用的家庭：管理后台登录一律视为无家庭（只看公共菜谱），其余取当前请求家庭。 */
    public static Long effectiveFamilyId() {
        return TenantSession.isAdminScope() ? null : TenantContextHolder.familyIdOrNull();
    }

    /** 给查询追加「公共 OR 本家」条件（可带表别名前缀，如 "d."）。 */
    public static <T> QueryWrapper<T> visible(QueryWrapper<T> q) {
        return visible(q, "");
    }

    public static <T> QueryWrapper<T> visible(QueryWrapper<T> q, String alias) {
        if (TenantContextHolder.isSystem()) return q;
        String col = alias + COLUMN;
        Long familyId = effectiveFamilyId();
        if (familyId == null) return q.isNull(col);
        return q.and(w -> w.isNull(col).or().eq(col, familyId));
    }

    /** 某条菜谱（familyId 可空 = 公共）对当前上下文是否可见。 */
    public static boolean isVisible(Long dishFamilyId) {
        if (dishFamilyId == null || TenantContextHolder.isSystem()) return true;
        return dishFamilyId.equals(effectiveFamilyId());
    }

    /** 当前上下文能否写这条菜谱：本家私有菜谱可写；公共菜谱只有平台运营可写（由调用方传入）。 */
    public static boolean isWritable(Long dishFamilyId, boolean operator) {
        if (TenantContextHolder.isSystem()) return true;
        if (dishFamilyId == null) return operator;
        return dishFamilyId.equals(effectiveFamilyId());
    }
}
