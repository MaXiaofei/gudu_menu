package com.gudu.xsd.modules.agent.application.privacy;

import com.gudu.xsd.modules.agent.domain.repository.AgentMessageRepository;
import com.gudu.xsd.modules.agent.domain.repository.AgentSessionRepository;
import com.gudu.xsd.modules.agent.domain.repository.AgentTraceRepository;
import com.gudu.xsd.modules.identity.application.AccountErasureStep;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 账号注销后续匿名化（隐私政策 §六、§七.6）：删除该账号的 AI 对话（消息 + 会话，含已软删除的），
 * 调用链去标识（清首句与片段输入输出、解除账号关联，统计保留）。幂等：无数据时全部为 0。
 */
@Component
@RequiredArgsConstructor
public class AgentAccountErasureStep implements AccountErasureStep {

    private final AgentMessageRepository messages;
    private final AgentSessionRepository sessions;
    private final AgentTraceRepository traces;

    @Override
    public String name() {
        return "AI 对话删除";
    }

    @Override
    public String erase(Long accountId) {
        int msg = messages.deleteByMember(accountId);
        List<String> ids = sessions.idsByMember(accountId);
        if (!ids.isEmpty()) messages.deleteBySessions(ids);
        int ses = sessions.hardDelete(ids);
        int tr = traces.anonymizeByMember(accountId);
        return "删除消息 " + msg + " 条、会话 " + ses + " 个，调用链去标识 " + tr + " 条";
    }
}
