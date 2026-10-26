package com.gudu.xsd.modules.identity.domain.model;

/**
 * 注销匿名化的一个步骤结果（值对象）。
 *
 * @param name   步骤名（如「账号匿名化」「意见反馈联系方式清除」）
 * @param ok     是否成功
 * @param detail 结果说明（影响条数 / 失败原因）
 */
public record ErasureStepResult(String name, boolean ok, String detail) {

    static final int DETAIL_MAX = 200;

    public ErasureStepResult {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("步骤名不能为空");
        if (detail != null && detail.length() > DETAIL_MAX) detail = detail.substring(0, DETAIL_MAX);
    }

    public static ErasureStepResult ok(String name, String detail) {
        return new ErasureStepResult(name, true, detail);
    }

    public static ErasureStepResult failed(String name, String detail) {
        return new ErasureStepResult(name, false, detail);
    }
}
