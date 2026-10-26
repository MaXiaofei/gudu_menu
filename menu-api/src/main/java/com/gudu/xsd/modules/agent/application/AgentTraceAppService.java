package com.gudu.xsd.modules.agent.application;

import com.gudu.xsd.common.BizException;
import com.gudu.xsd.modules.agent.application.dto.AgentViews.AgentStats;
import com.gudu.xsd.modules.agent.application.dto.AgentViews.FullTrace;
import com.gudu.xsd.modules.ai.gateway.domain.service.Pseudonymizer;
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

/**
 * 后台：咕嘟调用链列表 / 详情 / 对话 / 当日统计。
 *
 * <p>隐私（mini-release-compliance 合并后修复）：列表、详情、对话<b>默认只给脱敏摘要</b>——称呼替换为「成员A」、手机号 /
 * openid 占位（{@link Pseudonymizer}）、正文截断，不显示昵称头像、工具调用与卡片内容；
 * 查看全文走 {@link #full}，由运营后台应用服务校验并记录原因到操作日志。
 */
@Service
@RequiredArgsConstructor
public class AgentTraceAppService {

    private final AgentTraceRepository traces;
    private final AgentMessageRepository messages;
    private final AgentAppService agent;
    private final MemberService members;
    private final Clock clock;

    /** 列表摘要首句最大长度。 */
    static final int BRIEF_FIRST_TEXT = 40;
    /** 详情摘要里片段输入输出 / 对话正文单段最大长度。 */
    static final int BRIEF_TEXT = 80;

    public PageView<TraceRow> page(String cursor, Integer size, Integer rating, String status) {
        Cursor c = Cursor.decode(cursor);
        int n = Cursor.size(size);
        TraceStatus st = status == null || status.isBlank() ? null : TraceStatus.valueOf(status.trim().toUpperCase());
        List<AgentTrace> list = traces.page(rating, st, c.at(), c.id(), n);
        Map<Long, UserLite> users = users(list.stream().map(AgentTrace::memberId).toList());
        String next = list.size() < n ? null
                : Cursor.encode(list.get(list.size() - 1).createdAt(), list.get(list.size() - 1).traceId());
        return new PageView<>(list.stream().map(t -> new TraceRow(t.traceId(), anonymousUser(t.memberId()),
                brief(t.firstText(), pseudonymizer(users.get(t.memberId())), BRIEF_FIRST_TEXT), t.ms(),
                t.status().name(), t.createdAt())).toList(), next);
    }

    /** 调用链详情（脱敏摘要）。 */
    public TraceDetail detail(String traceId) {
        return detail(load(traceId), false);
    }

    /** 该调用链所在会话的对话（脱敏摘要：正文截断、不含工具调用 / 卡片 / 待确认动作）。 */
    public List<MessageView> conversation(String traceId) {
        return conversation(load(traceId), false);
    }

    /**
     * 查看全文（仅供运营后台应用服务在校验原因、写操作日志后调用）：未脱敏的调用链详情 + 完整对话。
     * 返回含对话所属账号（写日志的操作对象）。
     */
    public FullTrace full(String traceId) {
        AgentTrace t = load(traceId);
        return new FullTrace(t.memberId(), detail(t, true), conversation(t, true));
    }

    private AgentTrace load(String traceId) {
        return traces.find(traceId).orElseThrow(() -> new BizException(404, "调用链不存在"));
    }

    private TraceDetail detail(AgentTrace t, boolean full) {
        UserLite owner = users(List.of(t.memberId())).get(t.memberId());
        Pseudonymizer p = pseudonymizer(owner);
        TraceHeader h = new TraceHeader(t.traceId(), t.sessionId(), full ? owner : anonymousUser(t.memberId()),
                full ? t.firstText() : brief(t.firstText(), p, BRIEF_FIRST_TEXT), t.status().name(), t.ms(),
                t.firstTokenMs(), t.tokens(), t.costFen(), t.promptVersion(), t.provider(), t.model(), t.phoneMasked(),
                t.feedback(), t.degraded(), t.createdAt());
        List<SpanView> spans = traces.spans(t.traceId()).stream().map(s -> new SpanView(s.spanId(), s.parentId(),
                s.type().code(), s.name(), s.startMs(), s.ms(), s.tokens(), s.costFen(), s.ok(),
                full ? s.input() : briefMap(s.input(), p), full ? s.output() : briefMap(s.output(), p))).toList();
        return new TraceDetail(h, spans);
    }

    private List<MessageView> conversation(AgentTrace t, boolean full) {
        if (t.sessionId() == null) return List.of();
        Pseudonymizer p = pseudonymizer(users(List.of(t.memberId())).get(t.memberId()));
        return messages.all(t.sessionId()).stream().map(agent::view)
                .map(m -> full ? m : new MessageView(m.messageId(), m.role(), brief(m.text(), p, BRIEF_TEXT), List.of(),
                        List.of(), List.of(), m.traceId(), m.rating(), m.createdAt())).toList();
    }

    // ---------------------------------------------------------------- 脱敏摘要

    /** 摘要里的用户：只留账号 id，不显示昵称与头像。 */
    static UserLite anonymousUser(Long memberId) {
        return memberId == null ? null : new UserLite(memberId, "用户" + memberId, null);
    }

    /** 对话所属人的称呼 → 「成员A」；其余称呼无法确定，靠账号标识脱敏 + 截断兜底。 */
    static Pseudonymizer pseudonymizer(UserLite owner) {
        return owner == null || owner.nickname() == null ? Pseudonymizer.NONE : Pseudonymizer.of(List.of(owner.nickname()));
    }

    /** 脱敏（称呼、手机号、openid）后截断到 max 字。 */
    static String brief(String text, Pseudonymizer p, int max) {
        if (text == null) return null;
        String masked = p.mask(text);
        return masked.length() <= max ? masked : masked.substring(0, max) + "…";
    }

    /** 片段输入输出：递归脱敏并截断所有字符串。 */
    static Map<String, Object> briefMap(Map<String, Object> m, Pseudonymizer p) {
        if (m == null) return null;
        Map<String, Object> out = new LinkedHashMap<>();
        m.forEach((k, v) -> out.put(k, briefValue(v, p)));
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Object briefValue(Object v, Pseudonymizer p) {
        if (v instanceof String s) return brief(s, p, BRIEF_TEXT);
        if (v instanceof Map<?, ?> mm) return briefMap((Map<String, Object>) mm, p);
        if (v instanceof Collection<?> c) return c.stream().map(x -> briefValue(x, p)).toList();
        return v;
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
