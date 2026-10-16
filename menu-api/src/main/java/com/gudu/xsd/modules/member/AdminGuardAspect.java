package com.gudu.xsd.modules.member;

import cn.dev33.satoken.stp.StpUtil;
import com.gudu.xsd.common.BizException;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * {@link com.gudu.xsd.common.security.RequireAdmin} 切面：要求登录账号为平台超管。
 * 以登录身份（loginId）判断，与「当前就餐成员」无关——切换就餐成员不能获得超管能力。
 * 未登录时 {@code StpUtil.getLoginIdAsLong()} 抛 NotLoginException → 401。
 */
@Aspect
@Component
@RequiredArgsConstructor
public class AdminGuardAspect {

    private final AdminChecker adminChecker;

    @Around("@annotation(com.gudu.xsd.common.security.RequireAdmin)"
            + " || @within(com.gudu.xsd.common.security.RequireAdmin)")
    public Object check(ProceedingJoinPoint pjp) throws Throwable {
        if (!adminChecker.isAdmin(StpUtil.getLoginIdAsLong())) {
            throw BizException.forbidden("无权限：仅平台管理员可操作");
        }
        return pjp.proceed();
    }
}
