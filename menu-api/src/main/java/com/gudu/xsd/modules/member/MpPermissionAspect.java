package com.gudu.xsd.modules.member;

import cn.dev33.satoken.stp.StpUtil;
import com.gudu.xsd.common.BizException;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 权限矩阵切面:拦截 {@link MpPerm} 注解方法,校验<b>登录账号</b>是否拥有该功能权限。
 *
 * <p>取值链路:Sa-Token loginId → MemberMapper 查 member(roleTags + mpPermissions)
 * → {@link MpPermissionService#resolveByMemberId(Long)} → 不含 key 抛 403。
 *
 * <p>权限属于登录账号，与「当前就餐成员」(session.currentMemberId)无关——
 * 否则切换就餐成员即可借用他人(含超管)权限。
 *
 * <p>未登录:{@code auth.login-check=true}(生产)时 401;本地/测试环境放开登录时放行。
 */
@Slf4j
@Aspect
@Component
public class MpPermissionAspect {

    private final MpPermissionService permSvc;
    private final boolean loginCheck;

    public MpPermissionAspect(MpPermissionService permSvc,
                              @Value("${auth.login-check:true}") boolean loginCheck) {
        this.permSvc = permSvc;
        this.loginCheck = loginCheck;
    }

    @Around("@annotation(mpPerm)")
    public Object check(ProceedingJoinPoint pjp, MpPerm mpPerm) throws Throwable {
        if (!StpUtil.isLogin()) {
            if (loginCheck) {
                StpUtil.checkLogin(); // 抛 NotLoginException → 401
            }
            return pjp.proceed();
        }
        if (!permSvc.hasPermission(StpUtil.getLoginIdAsLong(), mpPerm.value())) {
            throw BizException.forbidden("无此功能权限");
        }
        return pjp.proceed();
    }
}
