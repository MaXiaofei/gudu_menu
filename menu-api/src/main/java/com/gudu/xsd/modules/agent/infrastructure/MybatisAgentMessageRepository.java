package com.gudu.xsd.modules.agent.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.gudu.xsd.modules.agent.domain.model.AgentMessage;
import com.gudu.xsd.modules.agent.domain.model.PendingAction;
import com.gudu.xsd.modules.agent.domain.model.ToolCallRecord;
import com.gudu.xsd.modules.agent.domain.repository.AgentMessageRepository;
import com.gudu.xsd.modules.agent.infrastructure.mapper.AgentMessageMapper;
import com.gudu.xsd.modules.agent.infrastructure.po.AgentMessagePO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class MybatisAgentMessageRepository implements AgentMessageRepository {

    private final AgentMessageMapper mapper;
    private final AgentJson json;

    @Override
    public void save(AgentMessage m) {
        AgentMessagePO po = new AgentMessagePO();
        po.setId(m.messageId());
        po.setSessionId(m.sessionId());
        po.setMemberId(m.memberId());
        po.setRole(m.role());
        po.setText(m.text());
        po.setToolsJson(json.write(m.tools()));
        po.setCardsJson(json.write(m.cards()));
        po.setActionsJson(json.write(m.actions()));
        po.setTraceId(m.traceId());
        po.setReasoning(m.reasoning());
        po.setRating(m.rating());
        po.setCreatedAt(m.createdAt());
        if (mapper.selectById(m.messageId()) == null) mapper.insert(po);
        else mapper.updateById(po);
    }

    @Override
    public Optional<AgentMessage> findById(String id) {
        return Optional.ofNullable(mapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public void updateRating(String messageId, int rating, String comment) {
        mapper.update(null, new UpdateWrapper<AgentMessagePO>().eq("id", messageId).set("rating", rating)
                .set("feedback_comment", comment));
    }

    @Override
    public void updateAction(String messageId, PendingAction action) {
        if (messageId == null) return;
        AgentMessagePO po = mapper.selectById(messageId);
        if (po == null) return;
        List<PendingAction> list = new ArrayList<>(json.list(po.getActionsJson(), PendingAction.class));
        boolean replaced = false;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).actionId().equals(action.actionId())) {
                list.set(i, action);
                replaced = true;
            }
        }
        if (!replaced) list.add(action);
        mapper.update(null, new UpdateWrapper<AgentMessagePO>().eq("id", messageId).set("actions_json", json.write(list)));
    }

    @Override
    public List<AgentMessage> listBySession(String sessionId, LocalDateTime beforeCreatedAt, String beforeId, int size) {
        QueryWrapper<AgentMessagePO> w = new QueryWrapper<AgentMessagePO>().eq("session_id", sessionId);
        if (beforeCreatedAt != null) {
            w.and(x -> x.lt("created_at", beforeCreatedAt)
                    .or(y -> y.eq("created_at", beforeCreatedAt).lt("id", beforeId)));
        }
        w.orderByDesc("created_at").orderByDesc("id").last("LIMIT " + size);
        return mapper.selectList(w).stream().map(this::toDomain).toList();
    }

    @Override
    public List<AgentMessage> recent(String sessionId, int n) {
        List<AgentMessage> desc = new ArrayList<>(listBySession(sessionId, null, null, n));
        Collections.reverse(desc);
        return desc;
    }

    @Override
    public List<AgentMessage> all(String sessionId) {
        return mapper.selectList(new QueryWrapper<AgentMessagePO>().eq("session_id", sessionId)
                .orderByAsc("created_at").orderByAsc("id").last("LIMIT 500")).stream().map(this::toDomain).toList();
    }

    private AgentMessage toDomain(AgentMessagePO p) {
        return new AgentMessage(p.getId(), p.getSessionId(), p.getMemberId(), p.getRole(), p.getText(),
                json.list(p.getToolsJson(), ToolCallRecord.class), json.maps(p.getCardsJson()),
                json.list(p.getActionsJson(), PendingAction.class), p.getTraceId(), p.getReasoning(), p.getRating(),
                p.getCreatedAt());
    }

    @Override
    public int deleteCreatedBefore(LocalDateTime cutoff, int limit) {
        return mapper.delete(new QueryWrapper<AgentMessagePO>().lt("created_at", cutoff).last("LIMIT " + Math.max(1, limit)));
    }

    @Override
    public int deleteByMember(Long memberId) {
        if (memberId == null) return 0;
        return mapper.delete(new QueryWrapper<AgentMessagePO>().eq("member_id", memberId));
    }

    @Override
    public int deleteBySessions(java.util.Collection<String> sessionIds) {
        if (sessionIds == null || sessionIds.isEmpty()) return 0;
        return mapper.delete(new QueryWrapper<AgentMessagePO>().in("session_id", sessionIds));
    }

    @Override
    public List<AgentMessage> allBySessions(java.util.Collection<String> sessionIds) {
        if (sessionIds == null || sessionIds.isEmpty()) return List.of();
        return mapper.selectList(new QueryWrapper<AgentMessagePO>().in("session_id", sessionIds)
                        .orderByAsc("created_at").orderByAsc("id"))
                .stream().map(this::toDomain).toList();
    }
}
