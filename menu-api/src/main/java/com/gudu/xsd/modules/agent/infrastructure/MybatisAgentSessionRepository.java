package com.gudu.xsd.modules.agent.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gudu.xsd.modules.agent.domain.model.AgentSession;
import com.gudu.xsd.modules.agent.domain.repository.AgentSessionRepository;
import com.gudu.xsd.modules.agent.infrastructure.mapper.AgentSessionMapper;
import com.gudu.xsd.modules.agent.infrastructure.po.AgentSessionPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class MybatisAgentSessionRepository implements AgentSessionRepository {

    private final AgentSessionMapper mapper;

    @Override
    public void save(AgentSession s) {
        AgentSessionPO po = new AgentSessionPO();
        po.setId(s.id());
        po.setMemberId(s.memberId());
        po.setTitle(s.title());
        po.setLastMessage(s.lastMessage());
        po.setCoverUrl(s.coverUrl());
        po.setCreatedAt(s.createdAt());
        po.setUpdatedAt(s.updatedAt());
        if (mapper.selectById(s.id()) == null) {
            po.setDeleted(0);
            mapper.insert(po);
        } else {
            mapper.updateById(po);
        }
    }

    @Override
    public Optional<AgentSession> findById(String id) {
        return Optional.ofNullable(mapper.selectById(id)).map(MybatisAgentSessionRepository::toDomain);
    }

    @Override
    public List<AgentSession> listByMember(Long memberId, LocalDateTime beforeUpdatedAt, String beforeId, int size) {
        QueryWrapper<AgentSessionPO> w = new QueryWrapper<AgentSessionPO>().eq("member_id", memberId);
        if (beforeUpdatedAt != null) {
            w.and(x -> x.lt("updated_at", beforeUpdatedAt)
                    .or(y -> y.eq("updated_at", beforeUpdatedAt).lt("id", beforeId)));
        }
        w.orderByDesc("updated_at").orderByDesc("id").last("LIMIT " + size);
        return mapper.selectList(w).stream().map(MybatisAgentSessionRepository::toDomain).toList();
    }

    private static AgentSession toDomain(AgentSessionPO p) {
        return AgentSession.restore(p.getId(), p.getMemberId(), p.getTitle(), p.getLastMessage(), p.getCoverUrl(),
                p.getCreatedAt(), p.getUpdatedAt());
    }

    @Override
    public List<String> idsUpdatedBefore(LocalDateTime cutoff, int limit) {
        return mapper.selectIdsUpdatedBefore(cutoff, Math.max(1, limit));
    }

    @Override
    public List<String> idsByMember(Long memberId) {
        return memberId == null ? List.of() : mapper.selectIdsByMember(memberId);
    }

    @Override
    public int hardDelete(java.util.Collection<String> ids) {
        if (ids == null || ids.isEmpty()) return 0;
        return mapper.hardDeleteByIds(ids);
    }

    @Override
    public List<AgentSession> listByMembers(java.util.Collection<Long> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) return List.of();
        return mapper.selectList(new QueryWrapper<AgentSessionPO>().in("member_id", memberIds)
                        .orderByDesc("updated_at").orderByDesc("id"))
                .stream().map(MybatisAgentSessionRepository::toDomain).toList();
    }
}
