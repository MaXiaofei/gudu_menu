package com.gudu.xsd.modules.member;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.gudu.xsd.common.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * 权限矩阵切面:拦截 {@link MpPerm} 注解方法,校验当前就餐成员是否拥有该功能权限。
 *
 * <p>取值链路:Sa-Token session.currentMemberId → MemberMapper 查 member(roleTags + mpPermissions)
 * → {@link MpPermissionService#resolveByMemberId(Long)} → 不含 key 抛 BizException。
 *
 * <p>session 无 currentMemberId(未选成员/未登录)时放行 —— 登录态由 Sa-Token 统一管,
 * 此切面只做「已选就餐成员的功能权限」细分,不重复鉴权。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class MpPermissionAspect {

    private final MpPermissionService permSvc;

    @Around("@annotation(mpPerm)")
    public Object check(ProceedingJoinPoint pjp, MpPerm mpPerm) throws Throwable {
        String key = mpPerm.value();
        Long memberId = currentMemberId();
        // 未选就餐成员:放行(由 Sa-Token 登录态统一管)
        if (memberId == null) {
            return pjp.proceed();
        }
        if (!permSvc.hasPermission(memberId, key)) {
            throw new BizException("无此功能权限");
        }
        return pjp.proceed();
    }

    /** 取当前就餐成员 id;session 无此 key 时返回 null(不抛,避免与登录鉴权重叠)。 */
    private Long currentMemberId() {
        try {
            SaSession session = StpUtil.getSession(false);
            if (session == null) return null;
            Object v = session.get("currentMemberId");
            if (v == null) return null;
            if (v instanceof Number n) return n.longValue();
            return Long.valueOf(String.valueOf(v));
        } catch (Exception e) {
            // 未登录/session 不存在等:由 Sa-Token 管登录,这里降级为「未选成员」放行
            return null;
        }
    }
}
