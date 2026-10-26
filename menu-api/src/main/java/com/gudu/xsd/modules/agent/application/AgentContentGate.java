package com.gudu.xsd.modules.agent.application;

import java.util.Optional;

/**
 * 对话内容安全闸门（A.6）：用户输入在发给模型前检测；模型回复在放出（token 事件）前复核。
 *
 * <p>流式友好：上游模型调用是整段返回（非流式），编排器拿到完整回复后先过 {@link #reviewReply}，
 * 再按 {@code TOKEN_CHUNK} 切片推 token 事件——违规内容一个字都不会先流出去，持久化的也是复核后的文本。
 */
public interface AgentContentGate {

    /** @return 违规时给用户的提示；通过为 empty */
    Optional<String> inputViolation(String text);

    /** @return 可放出的回复：违规时替换为安全提示 */
    String reviewReply(String reply);

    /** 不检测（评测 / 单测）。 */
    AgentContentGate PASS = new AgentContentGate() {
        @Override
        public Optional<String> inputViolation(String text) {
            return Optional.empty();
        }

        @Override
        public String reviewReply(String reply) {
            return reply;
        }
    };
}
