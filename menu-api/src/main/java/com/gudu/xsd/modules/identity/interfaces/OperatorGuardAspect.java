package com.gudu.xsd.modules.identity.interfaces;

import cn.dev33.satoken.stp.StpUtil;
import com.gudu.xsd.common.BizException;
import com.gudu.xsd.modules.identity.application.AccountQueryApi;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * {@link com.gudu.xsd.common.security.RequireOperator} 切面：登录账号平台角色须为 OPERATOR 或 SUPER_ADMIN
 * （非 ACTIVE 账号一律拒绝）。与家庭角色无关。未登录时 getLoginIdAsLong 抛 NotLoginException → 401。
 */
@Aspect
@Component
@RequiredArgsConstructor
public class OperatorGuardAspect {

    private final AccountQueryApi accountQuery;

    @Around("@annotation(com.gudu.xsd.common.security.RequireOperator)"
            + " || @within(com.gudu.xsd.common.security.RequireOperator)")
    public Object check(ProceedingJoinPoint pjp) throws Throwable {
        if (!accountQuery.isOperator(StpUtil.getLoginIdAsLong())) {
            throw BizException.forbidden("无权限：仅平台运营可操作");
        }
        return pjp.proceed();
    }
}
