package com.gudu.xsd.common.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 平台超管专属接口（AI 供应商/调用日志/用量、向量重建、营养指标与字典维护等）。
 * 未登录 → 401；登录账号非超管 → 403。由 member 模块的 AdminGuardAspect 校验。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireAdmin {
}
