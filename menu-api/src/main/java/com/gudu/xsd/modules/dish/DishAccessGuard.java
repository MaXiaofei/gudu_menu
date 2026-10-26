package com.gudu.xsd.modules.dish;

import cn.dev33.satoken.stp.StpUtil;
import com.gudu.xsd.common.BizException;
import com.gudu.xsd.common.security.TenantSession;
import com.gudu.xsd.common.tenant.DishVisibility;
import com.gudu.xsd.common.tenant.TenantContextHolder;
import com.gudu.xsd.modules.identity.application.AccountQueryApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 菜谱写权限（design §4.3，E.1）：
 * <ul>
 *   <li>新建：管理后台登录（scope=admin）的平台运营 → 公共菜谱；其他人 → 当前家庭私有菜谱；</li>
 *   <li>修改 / 删除：本家私有菜谱 → 本家成员可写；公共菜谱 → 仅平台运营（OPERATOR+）可写，其余 403；
 *       别家私有菜谱 → 404（不泄露存在性）。</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class DishAccessGuard {

    private final AccountQueryApi accounts;

    /** 新建菜谱时决定归属。 */
    public void assignOwnerForCreate(Dish dish) {
        dish.setSourceDishId(null);
        if (TenantSession.isAdminScope() && isOperator()) {
            dish.setFamilyId(null);
            return;
        }
        dish.setFamilyId(TenantContextHolder.requireFamilyId());
    }

    /** 修改 / 删除前校验；existing 为按可见性读到的菜谱（读不到 = 不存在或别家私有）。 */
    public void checkWritable(Dish existing) {
        if (existing == null) throw BizException.notFound("菜谱不存在");
        if (existing.getFamilyId() == null) {
            if (!isOperator()) throw BizException.forbidden("公共菜谱仅平台运营可修改，可先「复制到我家」再改");
            return;
        }
        if (!DishVisibility.isWritable(existing.getFamilyId(), false)) throw BizException.notFound("菜谱不存在");
    }

    public boolean isOperator() {
        try {
            return StpUtil.isLogin() && accounts.isOperator(StpUtil.getLoginIdAsLong());
        } catch (RuntimeException e) {
            return false;
        }
    }
}
