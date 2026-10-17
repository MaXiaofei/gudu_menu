package com.gudu.xsd.modules.ai.gateway.domain.model;

/**
 * 规则兜底：降级链最后一环（Mock）。调用方按自身业务给出确定性回复，
 * 例如咕嘟 Agent 的规则意图解析。未提供时网关在全部真实模型失败后抛出不可用异常，由调用方自行兜底。
 */
@FunctionalInterface
public interface RuleResponder {

    ModelReply reply(ChatRequest request);
}
