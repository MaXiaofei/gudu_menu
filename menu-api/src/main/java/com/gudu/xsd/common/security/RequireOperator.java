package com.gudu.xsd.common.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 运营后台接口：要求登录账号的平台角色为 OPERATOR 或 SUPER_ADMIN（multi-tenant-family F.3）。
 * 未登录 → 401；平台角色不足 → 403。由 identity 上下文的 OperatorGuardAspect 校验。
 * 仅超管可做的系统配置类接口（模型网关、字典、营养指标…）仍用 {@link RequireAdmin}。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireOperator {
}
