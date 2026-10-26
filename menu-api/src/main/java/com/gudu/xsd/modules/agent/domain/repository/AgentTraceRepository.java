package com.gudu.xsd.modules.agent.domain.repository;

import com.gudu.xsd.modules.agent.domain.model.AgentTrace;
import com.gudu.xsd.modules.agent.domain.model.TraceSpan;
import com.gudu.xsd.modules.agent.domain.model.TraceStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** 调用链仓储（agent_trace + agent_span）。 */
public interface AgentTraceRepository {

    void save(AgentTrace trace, List<TraceSpan> spans);

    Optional<AgentTrace> find(String traceId);

    List<TraceSpan> spans(String traceId);

    /** 注销：该账号的调用链去标识——清片段输入输出、首句，并解除与账号的关联（统计保留）。返回处理的调用链数。 */
    int anonymizeByMember(Long memberId);

    /** 倒序分页；rating=-1 只看差评；status 可空。 */
    List<AgentTrace> page(Integer rating, TraceStatus status, LocalDateTime beforeCreatedAt, String beforeId, int size);

    List<AgentTrace> since(LocalDateTime from);

    List<TraceSpan> spansOf(List<String> traceIds);

    void updateFeedback(String traceId, int rating);

    /** 保存期限清理（C.1）：物理删除 createdAt 早于 cutoff 的一批调用链（含其 span），返回删除的调用链条数。 */
    int purgeBefore(LocalDateTime cutoff, int limit);
}
