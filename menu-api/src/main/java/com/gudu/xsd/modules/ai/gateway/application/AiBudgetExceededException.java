package com.gudu.xsd.modules.ai.gateway.application;

/**
 * 全站今日 AI 花费已达上限且调用方没有规则兜底。属于「模型不可用」的一种（调用方原有的降级逻辑照常生效），
 * 但带独立业务码 {@link #CODE}，客户端可据此展示「额度用完」而不是笼统的「暂不可用」。
 */
public class AiBudgetExceededException extends ModelUnavailableException {

    public static final int CODE = 453;
    public static final String MESSAGE = "AI 服务今日额度已用完，明天自动恢复";

    public AiBudgetExceededException() {
        super(CODE, MESSAGE);
    }
}
