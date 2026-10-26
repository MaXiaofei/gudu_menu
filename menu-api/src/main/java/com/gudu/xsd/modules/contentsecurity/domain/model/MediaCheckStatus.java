package com.gudu.xsd.modules.contentsecurity.domain.model;

/** 图片检测状态。 */
public enum MediaCheckStatus {
    /** 已提交微信，等待回调。 */
    PENDING,
    PASS,
    REVIEW,
    /** 违规：文件已隔离，所有引用处不可见。 */
    RISKY,
    /** 提交微信失败（接口不可用 / 未配置公网地址）。 */
    SUBMIT_FAILED,
    /** 微信回调报检测失败（如拉图失败）。 */
    CHECK_FAILED;

    public static MediaCheckStatus of(String s) {
        try {
            return s == null ? PENDING : MediaCheckStatus.valueOf(s);
        } catch (IllegalArgumentException e) {
            return PENDING;
        }
    }
}
