package com.gudu.xsd.modules.agent.domain.repository;

import com.gudu.xsd.modules.agent.domain.model.AgentSession;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** 会话仓储。 */
public interface AgentSessionRepository {

    void save(AgentSession session);

    Optional<AgentSession> findById(String id);

    /** 按 updatedAt 倒序；游标为上一页最后一条的 (updatedAt, id)。 */
    List<AgentSession> listByMember(Long memberId, LocalDateTime beforeUpdatedAt, String beforeId, int size);

    /** 保存期限清理（C.1）：updatedAt 早于 cutoff 的会话 id（含已软删除），最多 limit 个。 */
    List<String> idsUpdatedBefore(LocalDateTime cutoff, int limit);

    /** 账号合并：把 from 档案的会话（含已软删除）改指 to 档案。 */
    int reassignMember(Long from, Long to);

    /** 注销：这些档案的全部会话 id（含已软删除）。 */
    List<String> idsByMembers(java.util.Collection<Long> memberIds);

    /** 物理删除会话（绕过逻辑删除）。 */
    int hardDelete(java.util.Collection<String> ids);

    /** 这些档案的全部未删除会话（导出我的数据，按更新时间倒序）。 */
    List<AgentSession> listByMembers(java.util.Collection<Long> memberIds);
}
