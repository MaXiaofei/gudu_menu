package com.gudu.xsd.modules.identity.domain.model;

/** 注销处理状态：已受理待匿名化 / 全部步骤完成 / 有步骤失败（可重试）。 */
public enum DeactivationStatus {
    PENDING, DONE, FAILED;

    public static DeactivationStatus of(String s) {
        try {
            return s == null ? PENDING : valueOf(s);
        } catch (IllegalArgumentException e) {
            return PENDING;
        }
    }
}
