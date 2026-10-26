package com.gudu.xsd.modules.agent.application;

import com.gudu.xsd.modules.ai.gateway.domain.service.Pseudonymizer;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SSE 出口的代号还原：token 事件逐块经 {@link Pseudonymizer.StreamRestorer} 还原（代号被拆在两个 token 里也能拼回），
 * done / error 前把扣住的尾巴补发为最后一个 token。其余事件原样透传。
 */
final class RestoringSink implements AgentEventSink {

    static final String TOKEN = "token";

    private final AgentEventSink inner;
    private final Pseudonymizer.StreamRestorer restorer;

    RestoringSink(AgentEventSink inner, Pseudonymizer pz) {
        this.inner = inner;
        this.restorer = pz.streamRestorer();
    }

    @Override
    public void send(String event, Map<String, Object> data) {
        if (TOKEN.equals(event) && data != null && data.get("text") instanceof String text) {
            String out = restorer.push(text);
            if (!out.isEmpty()) inner.send(TOKEN, withText(data, out));
            return;
        }
        if ("done".equals(event) || "error".equals(event)) {
            String rest = restorer.flush();
            if (!rest.isEmpty()) inner.send(TOKEN, Map.of("text", rest));
        }
        inner.send(event, data);
    }

    private static Map<String, Object> withText(Map<String, Object> data, String text) {
        Map<String, Object> m = new LinkedHashMap<>(data);
        m.put("text", text);
        return m;
    }
}
