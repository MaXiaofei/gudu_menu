package com.gudu.xsd.modules.identity.application;

/**
 * 注销后续匿名化步骤（mini-release-compliance F.2，开放给各上下文实现）：
 * 账号匿名化与家庭处理与注销同事务完成；按账号归属、不随家庭保留的其余个人数据由各上下文实现本接口，
 * 在注销提交后执行。实现必须<b>幂等</b>（后台可重试），以系统模式运行（不受租户过滤，SQL 自带账号条件）。
 */
public interface AccountErasureStep {

    /** 步骤名（后台展示，如「意见反馈联系方式清除」）。 */
    String name();

    /**
     * 执行匿名化。
     *
     * @return 结果说明（如「清除 3 条」）
     */
    String erase(Long accountId);
}
