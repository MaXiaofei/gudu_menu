package com.gudu.xsd.modules.identity.domain.service;

/**
 * 登录失败限流端口：同一登录名连续失败达到上限后锁定一段时间。
 * 手机号密码登录与「微信账号并入手机号账号」共用同一计数（防借合并接口撞库）。
 */
public interface LoginThrottle {

    /** 是否已被锁定。 */
    boolean isLocked(String loginName);

    void recordFailure(String loginName);

    void reset(String loginName);
}
