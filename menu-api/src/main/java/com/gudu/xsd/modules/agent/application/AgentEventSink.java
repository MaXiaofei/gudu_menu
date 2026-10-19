package com.gudu.xsd.modules.agent.application;

import java.util.Map;

/**
 * 对话事件出口（contracts.md §1 SSE 事件）：线上实现写 SseEmitter，评测与单测实现为内存记录。
 */
public interface AgentEventSink {

    void send(String event, Map<String, Object> data);

    /** 不需要输出的场景（后台重放等）。 */
    AgentEventSink NOOP = (e, d) -> { };
}
