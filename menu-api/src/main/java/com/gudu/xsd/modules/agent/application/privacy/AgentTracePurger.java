package com.gudu.xsd.modules.agent.application.privacy;

import com.gudu.xsd.common.privacy.RetentionPurger;
import com.gudu.xsd.modules.agent.domain.repository.AgentTraceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** AI 调用日志 180 天：咕嘟调用链 agent_trace + agent_span（与 ai_call_log 同一保存期限策略）。 */
@Component
@RequiredArgsConstructor
public class AgentTracePurger implements RetentionPurger {

    private final AgentTraceRepository traces;

    @Override
    public String policy() {
        return "ai-call-log";
    }

    @Override
    public int defaultDays() {
        return 180;
    }

    @Override
    public int purgeBatch(LocalDateTime cutoff, int limit) {
        return traces.purgeBefore(cutoff, limit);
    }
}
