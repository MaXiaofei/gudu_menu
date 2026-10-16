package com.gudu.xsd.common.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * AI 调用按登录账号计每日次数（gudu.ai.daily-limit），超限返回 429。
 * 计数以服务端登录身份为准，不信任请求参数里的 memberId。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AiRateLimited {
}
