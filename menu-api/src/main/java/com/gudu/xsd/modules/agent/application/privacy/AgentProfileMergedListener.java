package com.gudu.xsd.modules.agent.application.privacy;

import com.gudu.xsd.modules.agent.domain.repository.AgentMessageRepository;
import com.gudu.xsd.modules.agent.domain.repository.AgentSessionRepository;
import com.gudu.xsd.modules.agent.domain.repository.AgentTraceRepository;
import com.gudu.xsd.modules.family.domain.event.ProfileMerged;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 账号合并时同家庭重复档案并入目标档案：AI 对话（会话 / 消息 / 调用链）按<b>成员档案 id</b>归属
 * （agent_*.member_id = member.id，见 CurrentMember），所以随档案改指，合并后目标账号的导出 / 注销才找得到。
 * 与合并同事务同步执行，幂等。
 */
@Component
@RequiredArgsConstructor
public class AgentProfileMergedListener {

    private final AgentSessionRepository sessions;
    private final AgentMessageRepository messages;
    private final AgentTraceRepository traces;

    @EventListener
    public void on(ProfileMerged e) {
        sessions.reassignMember(e.fromProfileId(), e.toProfileId());
        messages.reassignMember(e.fromProfileId(), e.toProfileId());
        traces.reassignMember(e.fromProfileId(), e.toProfileId());
    }
}
