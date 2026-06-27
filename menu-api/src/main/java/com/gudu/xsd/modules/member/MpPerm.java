package com.gudu.xsd.modules.member;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 小程序功能权限注解。标在 Controller 方法上,由 {@link MpPermissionAspect} 拦截校验。
 *
 * <p>语义:从 Sa-Token session 取 currentMemberId(当前就餐成员),查其 roleTags + mpPermissions,
 * 解析出权限集合;若不含 {@link #value()} 指定的 key,抛 BizException("无此功能权限")。
 *
 * <p>session 无 currentMemberId(未选成员/未登录场景)时放行 —— 登录态由 Sa-Token 统一管,
 * 此注解只做「已选就餐成员的功能权限」细分。
 *
 * <p>功能 key 取值(代码层英文):dish.create / dish.edit / menu.plan / menu.view /
 * pantry.manage / shopping.generate / review.create / health.view。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface MpPerm {
    /** 需要的功能权限 key。 */
    String value();
}
