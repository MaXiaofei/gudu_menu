package com.gudu.xsd.modules.agent.application.privacy;

import com.gudu.xsd.common.privacy.PersonalDataSection;
import com.gudu.xsd.common.privacy.PersonalDataSubject;
import com.gudu.xsd.modules.agent.domain.model.AgentMessage;
import com.gudu.xsd.modules.agent.domain.model.AgentSession;
import com.gudu.xsd.modules.agent.domain.repository.AgentMessageRepository;
import com.gudu.xsd.modules.agent.domain.repository.AgentSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 导出我的数据（C.2）：本人档案的咕嘟 AI 对话（会话 + 消息正文 / 角色 / 时间 / 评分）。
 * 不导出模型思考内容与内部工具调用快照（非用户提供的个人信息）。agent_* 为全局表，按 member_id ∈ 本人档案过滤。
 */
@Component
@RequiredArgsConstructor
public class AgentPersonalData implements PersonalDataSection {

    private final AgentSessionRepository sessions;
    private final AgentMessageRepository messages;

    @Override
    public String key() {
        return "aiConversations";
    }

    @Override
    public int order() {
        return 70;
    }

    @Override
    public Object export(PersonalDataSubject s) {
        if (s.profileIds().isEmpty()) return List.of();
        List<AgentSession> list = sessions.listByMembers(s.profileIds());
        if (list.isEmpty()) return List.of();
        Map<String, List<AgentMessage>> bySession = messages.allBySessions(list.stream().map(AgentSession::id).toList())
                .stream().collect(Collectors.groupingBy(AgentMessage::sessionId));
        return list.stream().map(se -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", se.id());
            m.put("title", se.title());
            m.put("createdAt", se.createdAt());
            m.put("updatedAt", se.updatedAt());
            m.put("messages", bySession.getOrDefault(se.id(), List.of()).stream().map(msg -> {
                Map<String, Object> x = new LinkedHashMap<>();
                x.put("role", msg.role());
                x.put("text", msg.text());
                x.put("rating", msg.rating());
                x.put("createdAt", msg.createdAt());
                return x;
            }).toList());
            return m;
        }).toList();
    }
}
