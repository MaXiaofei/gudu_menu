package com.gudu.xsd.modules.agent.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.gudu.xsd.modules.agent.domain.model.AgentTrace;
import com.gudu.xsd.modules.agent.domain.model.SpanType;
import com.gudu.xsd.modules.agent.domain.model.TraceSpan;
import com.gudu.xsd.modules.agent.domain.model.TraceStatus;
import com.gudu.xsd.modules.agent.domain.repository.AgentTraceRepository;
import com.gudu.xsd.modules.agent.infrastructure.mapper.AgentSpanMapper;
import com.gudu.xsd.modules.agent.infrastructure.mapper.AgentTraceMapper;
import com.gudu.xsd.modules.agent.infrastructure.po.AgentSpanPO;
import com.gudu.xsd.modules.agent.infrastructure.po.AgentTracePO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class MybatisAgentTraceRepository implements AgentTraceRepository {

    static final int MAX_STATS_ROWS = 20_000;

    private final AgentTraceMapper traceMapper;
    private final AgentSpanMapper spanMapper;
    private final AgentJson json;

    @Override
    public void save(AgentTrace t, List<TraceSpan> spans) {
        AgentTracePO po = new AgentTracePO();
        po.setId(t.traceId());
        po.setSessionId(t.sessionId());
        po.setMessageId(t.messageId());
        po.setMemberId(t.memberId());
        po.setFirstText(t.firstText() == null ? null : t.firstText().length() > 250 ? t.firstText().substring(0, 250)
                : t.firstText());
        po.setStatus(t.status().name());
        po.setProvider(t.provider());
        po.setModel(t.model());
        po.setPromptVersion(t.promptVersion());
        po.setDegraded(t.degraded() ? 1 : 0);
        po.setMs((int) t.ms());
        po.setFirstTokenMs(t.firstTokenMs() == null ? null : t.firstTokenMs().intValue());
        po.setPromptTokens(t.promptTokens());
        po.setCompletionTokens(t.completionTokens());
        po.setCostFen(t.costFen());
        po.setPhoneMasked(t.phoneMasked());
        po.setFeedback(t.feedback());
        po.setCreatedAt(t.createdAt());
        traceMapper.insert(po);
        for (TraceSpan s : spans) {
            AgentSpanPO sp = new AgentSpanPO();
            sp.setTraceId(t.traceId());
            sp.setSpanId(s.spanId());
            sp.setParentId(s.parentId());
            sp.setType(s.type().code());
            sp.setName(s.name().length() > 64 ? s.name().substring(0, 64) : s.name());
            sp.setStartMs((int) s.startMs());
            sp.setMs((int) s.ms());
            sp.setTokens(s.tokens());
            sp.setCostFen(s.costFen());
            sp.setOk(s.ok() ? 1 : 0);
            sp.setInputJson(clip(json.write(s.input())));
            sp.setOutputJson(clip(json.write(s.output())));
            spanMapper.insert(sp);
        }
    }

    @Override
    public Optional<AgentTrace> find(String traceId) {
        return Optional.ofNullable(traceMapper.selectById(traceId)).map(MybatisAgentTraceRepository::toDomain);
    }

    @Override
    public List<TraceSpan> spans(String traceId) {
        return spansOf(List.of(traceId));
    }

    @Override
    public List<TraceSpan> spansOf(List<String> traceIds) {
        if (traceIds.isEmpty()) return List.of();
        return spanMapper.selectList(new QueryWrapper<AgentSpanPO>().in("trace_id", traceIds).orderByAsc("id"))
                .stream().map(this::toSpan).toList();
    }

    @Override
    public List<AgentTrace> page(Integer rating, TraceStatus status, LocalDateTime beforeCreatedAt, String beforeId,
                                 int size) {
        QueryWrapper<AgentTracePO> w = new QueryWrapper<>();
        if (rating != null) w.eq("feedback", rating);
        if (status != null) w.eq("status", status.name());
        if (beforeCreatedAt != null) {
            w.and(x -> x.lt("created_at", beforeCreatedAt)
                    .or(y -> y.eq("created_at", beforeCreatedAt).lt("id", beforeId)));
        }
        w.orderByDesc("created_at").orderByDesc("id").last("LIMIT " + size);
        return traceMapper.selectList(w).stream().map(MybatisAgentTraceRepository::toDomain).toList();
    }

    @Override
    public List<AgentTrace> since(LocalDateTime from) {
        return traceMapper.selectList(new QueryWrapper<AgentTracePO>().ge("created_at", from)
                        .orderByDesc("created_at").last("LIMIT " + MAX_STATS_ROWS))
                .stream().map(MybatisAgentTraceRepository::toDomain).toList();
    }

    @Override
    public void updateFeedback(String traceId, int rating) {
        UpdateWrapper<AgentTracePO> u = new UpdateWrapper<AgentTracePO>().eq("id", traceId).set("feedback", rating);
        if (rating < 0) u.set("status", TraceStatus.DOWNVOTED.name());
        traceMapper.update(null, u);
    }

    private static AgentTrace toDomain(AgentTracePO p) {
        return new AgentTrace(p.getId(), p.getSessionId(), p.getMessageId(), p.getMemberId(), p.getFirstText(),
                TraceStatus.valueOf(p.getStatus()), p.getProvider(), p.getModel(), p.getPromptVersion(),
                Integer.valueOf(1).equals(p.getDegraded()), p.getMs() == null ? 0 : p.getMs(),
                p.getFirstTokenMs() == null ? null : p.getFirstTokenMs().longValue(), nz(p.getPromptTokens()),
                nz(p.getCompletionTokens()), p.getCostFen(), nz(p.getPhoneMasked()), p.getFeedback(), p.getCreatedAt());
    }

    private TraceSpan toSpan(AgentSpanPO p) {
        return new TraceSpan(p.getSpanId(), p.getParentId(), SpanType.valueOf(p.getType().toUpperCase()), p.getName(),
                nz(p.getStartMs()), nz(p.getMs()), nz(p.getTokens()), p.getCostFen(), Integer.valueOf(1).equals(p.getOk()),
                json.map(p.getInputJson()), json.map(p.getOutputJson()));
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }

    private static String clip(String s) {
        return s == null || s.length() <= 8000 ? s : s.substring(0, 8000);
    }

    @Override
    public int anonymizeByMember(Long memberId) {
        if (memberId == null) return 0;
        List<String> ids = traceMapper.selectList(new QueryWrapper<AgentTracePO>().select("id")
                .eq("member_id", memberId)).stream().map(AgentTracePO::getId).toList();
        if (ids.isEmpty()) return 0;
        spanMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<AgentSpanPO>()
                .in("trace_id", ids).set("input_json", null).set("output_json", null));
        return traceMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<AgentTracePO>()
                .in("id", ids).set("member_id", null).set("first_text", null));
    }

    @Override
    public int purgeBefore(LocalDateTime cutoff, int limit) {
        List<String> ids = traceMapper.selectList(new QueryWrapper<AgentTracePO>().select("id")
                        .lt("created_at", cutoff).orderByAsc("created_at").last("LIMIT " + Math.max(1, limit)))
                .stream().map(AgentTracePO::getId).toList();
        if (ids.isEmpty()) return 0;
        spanMapper.delete(new QueryWrapper<AgentSpanPO>().in("trace_id", ids));
        return traceMapper.deleteByIds(ids);
    }
}
