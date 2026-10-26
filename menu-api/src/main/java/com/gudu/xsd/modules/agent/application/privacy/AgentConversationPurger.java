package com.gudu.xsd.modules.agent.application.privacy;

import com.gudu.xsd.common.privacy.RetentionPurger;
import com.gudu.xsd.modules.agent.domain.repository.AgentMessageRepository;
import com.gudu.xsd.modules.agent.domain.repository.AgentSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 对话记录保存 180 天（隐私政策 §三.2 / §六.2）：先按消息时间删超期消息，再删最后活跃早于期限的会话（含已软删除的）。
 * 每批独立提交。
 */
@Component
@RequiredArgsConstructor
public class AgentConversationPurger implements RetentionPurger {

    private final AgentMessageRepository messages;
    private final AgentSessionRepository sessions;

    @Override
    public String policy() {
        return "agent-conversation";
    }

    @Override
    public int defaultDays() {
        return 180;
    }

    @Override
    public int purgeBatch(LocalDateTime cutoff, int limit) {
        int n = messages.deleteCreatedBefore(cutoff, limit);
        if (n > 0) return n;
        List<String> ids = sessions.idsUpdatedBefore(cutoff, limit);
        if (ids.isEmpty()) return 0;
        messages.deleteBySessions(ids);
        return Math.max(1, sessions.hardDelete(ids));
    }
}
