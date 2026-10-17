package com.gudu.xsd.modules.agent.application;

import com.gudu.xsd.common.BizException;
import com.gudu.xsd.modules.agent.application.dto.AgentViews.AgentStats;
import com.gudu.xsd.modules.agent.application.dto.AgentViews.MessageView;
import com.gudu.xsd.modules.agent.application.dto.AgentViews.PageView;
import com.gudu.xsd.modules.agent.application.dto.AgentViews.SpanView;
import com.gudu.xsd.modules.agent.application.dto.AgentViews.ToolStat;
import com.gudu.xsd.modules.agent.application.dto.AgentViews.TraceDetail;
import com.gudu.xsd.modules.agent.application.dto.AgentViews.TraceHeader;
import com.gudu.xsd.modules.agent.application.dto.AgentViews.TraceRow;
import com.gudu.xsd.modules.agent.application.dto.AgentViews.UserLite;
import com.gudu.xsd.modules.agent.domain.model.AgentTrace;
import com.gudu.xsd.modules.agent.domain.model.SpanType;
import com.gudu.xsd.modules.agent.domain.model.TraceSpan;
import com.gudu.xsd.modules.agent.domain.model.TraceStatus;
import com.gudu.xsd.modules.agent.domain.repository.AgentMessageRepository;
import com.gudu.xsd.modules.agent.domain.repository.AgentTraceRepository;
import com.gudu.xsd.modules.member.Member;
import com.gudu.xsd.modules.member.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** 后台：咕嘟调用链列表 / 详情 / 完整对话 / 当日统计。 */
@Service
@RequiredArgsConstructor
public class AgentTraceAppService {

    private final AgentTraceRepository traces;
    private final AgentMessageRepository messages;
    private final AgentAppService agent;
    private final MemberService members;
    private final Clock clock;

    public PageView<TraceRow> page(String cursor, Integer size, Integer rating, String status) {
        Cursor c = Cursor.decode(cursor);
        int n = Cursor.size(size);
        TraceStatus st = status == null || status.isBlank() ? null : TraceStatus.valueOf(status.trim().toUpperCase());
        List<AgentTrace> list = traces.page(rating, st, c.at(), c.id(), n);
        Map<Long, UserLite> users = users(list.stream().map(AgentTrace::memberId).toList());
        String next = list.size() < n ? null
                : Cursor.encode(list.get(list.size() - 1).createdAt(), list.get(list.size() - 1).traceId());
        return new PageView<>(list.stream().map(t -> new TraceRow(t.traceId(), users.get(t.memberId()), t.firstText(),
                t.ms(), t.status().name(), t.createdAt())).toList(), next);
    }

    public TraceDetail detail(String traceId) {
        AgentTrace t = traces.find(traceId).orElseThrow(() -> new BizException(404, "调用链不存在"));
        UserLite u = users(List.of(t.memberId())).get(t.memberId());
        TraceHeader h = new TraceHeader(t.traceId(), t.sessionId(), u, t.firstText(), t.status().name(), t.ms(),
                t.firstTokenMs(), t.tokens(), t.costFen(), t.promptVersion(), t.provider(), t.model(), t.phoneMasked(),
                t.feedback(), t.degraded(), t.createdAt());
        List<SpanView> spans = traces.spans(traceId).stream().map(s -> new SpanView(s.spanId(), s.parentId(),
                s.type().code(), s.name(), s.startMs(), s.ms(), s.tokens(), s.costFen(), s.ok(), s.input(), s.output()))
                .toList();
        return new TraceDetail(h, spans);
    }

    /** 该调用链所在会话的完整对话（「查看完整对话」）。 */
    public List<MessageView> conversation(String traceId) {
        AgentTrace t = traces.find(traceId).orElseThrow(() -> new BizException(404, "调用链不存在"));
        if (t.sessionId() == null) return List.of();
        return messages.all(t.sessionId()).stream().map(agent::view).toList();
    }

    public AgentStats stats(LocalDate date) {
        LocalDate d = date == null ? LocalDate.now(clock) : date;
        List<AgentTrace> day = traces.since(d.atStartOfDay()).stream()
                .filter(t -> t.createdAt() != null && t.createdAt().toLocalDate().equals(d)).toList();
        List<TraceSpan> spans = traces.spansOf(day.stream().map(AgentTrace::traceId).toList());
        List<TraceSpan> toolSpans = spans.stream()
                .filter(s -> s.type() == SpanType.TOOL || s.type() == SpanType.RETRIEVAL).toList();
        double success = toolSpans.isEmpty() ? 1.0 : toolSpans.stream().filter(TraceSpan::ok).count() / (double) toolSpans.size();
        Map<String, List<TraceSpan>> byName = new LinkedHashMap<>();
        for (TraceSpan s : toolSpans) {
            byName.computeIfAbsent(s.name().replaceFirst("^(tool|retrieval)\\.", ""), k -> new ArrayList<>()).add(s);
        }
        List<ToolStat> byTool = byName.entrySet().stream().map(e -> new ToolStat(e.getKey(), e.getValue().size(),
                        e.getValue().stream().filter(TraceSpan::ok).count() / (double) e.getValue().size(),
                        p95(e.getValue().stream().map(TraceSpan::ms).toList())))
                .sorted((a, b) -> Long.compare(b.calls(), a.calls())).toList();
        BigDecimal cost = day.stream().map(AgentTrace::costFen).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long guard = day.stream().filter(t -> t.status() == TraceStatus.GUARDED).count();
        long p95First = p95(day.stream().map(AgentTrace::firstTokenMs).filter(Objects::nonNull).toList());
        return new AgentStats(d.toString(), day.size(), success, p95First, cost, guard, byTool);
    }

    private Map<Long, UserLite> users(Collection<Long> ids) {
        List<Long> valid = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (valid.isEmpty()) return Map.of();
        return members.listByIds(valid).stream().collect(Collectors.toMap(Member::getId,
                m -> new UserLite(m.getId(), m.getName(), m.getAvatarUrl()), (a, b) -> a));
    }

    static long p95(List<Long> values) {
        if (values.isEmpty()) return 0;
        List<Long> s = values.stream().sorted().toList();
        return s.get(Math.max(0, (int) Math.ceil(s.size() * 0.95) - 1));
    }
}
