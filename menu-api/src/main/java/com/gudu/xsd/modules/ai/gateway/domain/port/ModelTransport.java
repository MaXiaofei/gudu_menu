package com.gudu.xsd.modules.ai.gateway.domain.port;

import com.gudu.xsd.modules.ai.gateway.domain.model.ChatRequest;
import com.gudu.xsd.modules.ai.gateway.domain.model.ModelReply;
import com.gudu.xsd.modules.ai.gateway.domain.model.ThinkingMode;

/** 模型传输端口（OpenAI 兼容 HTTP 实现在 infrastructure）。 */
public interface ModelTransport {

    /**
     * 发一次请求。
     *
     * @param apiKey 明文 key，只在本次调用的栈上存在，禁止记录
     * @throws TransportException 网络/超时/非 2xx/解析失败
     */
    ModelReply call(Endpoint endpoint, String apiKey, ChatRequest request, ThinkingMode thinking, int timeoutMs);

    /**
     * 调用目标。
     *
     * @param supportsThinking 为 false 时不发送 thinking / reasoning_effort 字段
     */
    record Endpoint(String providerName, String baseUrl, String model, boolean supportsThinking) {
    }
}
