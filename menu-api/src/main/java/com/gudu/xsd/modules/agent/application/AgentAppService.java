package com.gudu.xsd.modules.agent.application;

import com.gudu.xsd.common.BizException;
import com.gudu.xsd.common.domain.DomainException;
import com.gudu.xsd.modules.agent.application.dto.AgentViews.ActionView;
import com.gudu.xsd.modules.agent.application.dto.AgentViews.ConfirmView;
import com.gudu.xsd.modules.agent.application.dto.AgentViews.ExpiringView;
import com.gudu.xsd.modules.agent.application.dto.AgentViews.MessageView;
import com.gudu.xsd.modules.agent.application.dto.AgentViews.PageView;
import com.gudu.xsd.modules.agent.application.dto.AgentViews.SendCommand;
import com.gudu.xsd.modules.agent.application.dto.AgentViews.SessionView;
import com.gudu.xsd.modules.agent.application.dto.AgentViews.SuggestionsView;
import com.gudu.xsd.modules.agent.application.dto.AgentViews.ToolView;
import com.gudu.xsd.modules.agent.application.kitchen.LiveKitchenFactory;
import com.gudu.xsd.modules.agent.application.tools.AgentTool;
import com.gudu.xsd.modules.agent.application.tools.MarkCookedTool;
import com.gudu.xsd.modules.agent.application.tools.ToolContext;
import com.gudu.xsd.modules.agent.application.tools.ToolOutcome;
import com.gudu.xsd.modules.agent.application.tools.ToolRegistry;
import com.gudu.xsd.modules.agent.domain.model.ActionStatus;
import com.gudu.xsd.modules.agent.domain.model.AgentMessage;
import com.gudu.xsd.modules.agent.domain.model.AgentSession;
import com.gudu.xsd.modules.agent.domain.model.AgentTrace;
import com.gudu.xsd.modules.agent.domain.model.PendingAction;
import com.gudu.xsd.modules.agent.domain.model.TraceStatus;
import com.gudu.xsd.modules.agent.domain.port.Kitchen;
import com.gudu.xsd.modules.agent.domain.port.Kitchen.DishInfo;
import com.gudu.xsd.modules.agent.domain.port.Kitchen.MemberInfo;
import com.gudu.xsd.modules.agent.domain.port.SpeechRecognizer;
import com.gudu.xsd.modules.agent.domain.repository.AgentMessageRepository;
import com.gudu.xsd.modules.agent.domain.repository.AgentQuota;
import com.gudu.xsd.modules.agent.domain.repository.AgentSessionRepository;
import com.gudu.xsd.modules.agent.domain.repository.AgentTraceRepository;
import com.gudu.xsd.modules.agent.domain.repository.PendingActionStore;
import com.gudu.xsd.modules.agent.domain.service.PhoneMasker;
import com.gudu.xsd.modules.ai.gateway.application.ModelGateway;
import com.gudu.xsd.modules.ai.gateway.domain.model.ChatMessage;
import com.gudu.xsd.modules.contentsecurity.application.ContentSecurityAppService;
import com.gudu.xsd.modules.preference.application.UserPreferenceAppService;
import com.gudu.xsd.modules.recommend.application.HomeRecommendAppService;
import com.gudu.xsd.modules.recommend.application.dto.DishCard;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 咕嘟用例：会话、发消息（SSE 由接口层驱动）、确认 / 取消 / 撤销 / 修改待确认卡、评价、首屏建议。
 * 每轮对话落库：用户消息、助手消息（工具 / 卡片 / 动作快照）、调用链。
 */
@Slf4j
@Service
public class AgentAppService {

    static final int HISTORY_MESSAGES = 20;
    /** 未通过内容安全的用户输入不落原文（也就不会作为历史再发给模型）。 */
    static final String BLOCKED_INPUT_PLACEHOLDER = "（该消息未通过内容安全检测，已屏蔽）";

    private final AgentSessionRepository sessions;
    private final AgentMessageRepository messages;
    private final AgentTraceRepository traces;
    private final PendingActionStore actionStore;
    private final AgentQuota quota;
    private final AgentOrchestrator orchestrator;
    private final ToolRegistry registry;
    private final LiveKitchenFactory kitchens;
    private final ModelGateway gateway;
    private final SpeechRecognizer speech;
    private final Clock clock;
    private final int dailyLimit;
    private final ContentSecurityAppService contentSecurity;
    private final UserPreferenceAppService preferences;
    private final HomeRecommendAppService homeRecommend;

    public AgentAppService(AgentSessionRepository sessions, AgentMessageRepository messages, AgentTraceRepository traces,
                           PendingActionStore actionStore, AgentQuota quota, AgentOrchestrator orchestrator,
                           ToolRegistry registry, LiveKitchenFactory kitchens, ModelGateway gateway,
                           SpeechRecognizer speech, Clock clock,
                           @Value("${gudu.agent.daily-limit:${gudu.ai.daily-limit:50}}") int dailyLimit,
                           ContentSecurityAppService contentSecurity, UserPreferenceAppService preferences,
                           HomeRecommendAppService homeRecommend) {
        this.preferences = preferences;
        this.homeRecommend = homeRecommend;
        this.sessions = sessions;
        this.messages = messages;
        this.traces = traces;
        this.actionStore = actionStore;
        this.quota = quota;
        this.orchestrator = orchestrator;
        this.registry = registry;
        this.kitchens = kitchens;
        this.gateway = gateway;
        this.speech = speech;
        this.clock = clock;
        this.dailyLimit = dailyLimit;
        this.contentSecurity = contentSecurity;
    }

    // ================================================================== 会话

    public String createSession(Long memberId, String title) {
        String id = "ses_" + uuid();
        sessions.save(AgentSession.start(id, memberId, title, now()));
        return id;
    }

    public PageView<SessionView> sessions(Long memberId, String cursor, Integer size) {
        Cursor c = Cursor.decode(cursor);
        int n = Cursor.size(size);
        List<AgentSession> list = sessions.listByMember(memberId, c.at(), c.id(), n);
        String next = list.size() < n ? null : Cursor.encode(list.get(list.size() - 1).updatedAt(), list.get(list.size() - 1).id());
        return new PageView<>(list.stream().map(s -> new SessionView(s.id(), s.title(), s.lastMessage(), s.updatedAt(),
                s.coverUrl())).toList(), next);
    }

    /**
     * 用户自行删除单个 AI 对话（隐私政策 §三.2）。只能删本人档案（member_id = 当前档案 id）的会话；
     * 不存在与不属于本人一律 404（不暴露他人会话是否存在）。物理删除消息与会话，调用链去标识（统计保留）。
     */
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public void deleteSession(Long memberId, String sessionId) {
        AgentSession s = sessions.findById(sessionId).orElseThrow(() -> new BizException(404, "会话不存在"));
        if (!s.ownedBy(memberId)) throw new BizException(404, "会话不存在");
        eraseSessions(List.of(sessionId));
    }

    /** 清空当前档案的全部 AI 对话（含已软删除的），返回删除的会话数。 */
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public int clearSessions(Long memberId) {
        List<String> ids = sessions.idsByMembers(List.of(memberId));
        if (ids.isEmpty()) return 0;
        eraseSessions(ids);
        return ids.size();
    }

    private void eraseSessions(List<String> ids) {
        messages.deleteBySessions(ids);
        traces.anonymizeBySessions(ids);
        sessions.hardDelete(ids);
    }

    public PageView<MessageView> messages(Long memberId, String sessionId, String cursor, Integer size) {
        own(memberId, sessionId);
        Cursor c = Cursor.decode(cursor);
        int n = Cursor.size(size);
        List<AgentMessage> list = messages.listBySession(sessionId, c.at(), c.id(), n);
        String next = list.size() < n ? null
                : Cursor.encode(list.get(list.size() - 1).createdAt(), list.get(list.size() - 1).messageId());
        return new PageView<>(list.stream().map(this::view).toList(), next);
    }

    // ================================================================== 对话

    /** 发一条消息并跑完一轮（调用方负责在后台线程执行并把 sink 接到 SSE）。 */
    public TurnResult send(Long memberId, String sessionId, SendCommand cmd, AgentEventSink sink) {
        return send(memberId, memberId, sessionId, cmd, sink);
    }

    /**
     * @param accountId 登录账号（内容安全按它取 openid；memberId 是当前就餐成员，可能是没有 openid 的家庭成员档案）
     */
    public TurnResult send(Long memberId, Long accountId, String sessionId, SendCommand cmd, AgentEventSink sink) {
        AgentSession session = own(memberId, sessionId);
        String traceId = "tr_" + uuid();
        String text = cmd.text() == null ? "" : cmd.text().trim();
        if (text.isEmpty() && cmd.audioUrl() != null && !cmd.audioUrl().isBlank()) {
            text = speech.transcribe(cmd.audioUrl());
            sink.send("transcript", Map.of("text", text, "mock", !speech.real()));
        }
        // 内容安全（A.6）：输入发模型前检测（微信不可用降级规则）；违规不落原文，由编排器走 GUARDED 回复
        Optional<String> unsafe = contentSecurity.agentInputViolation(accountId, text);
        if (unsafe.isPresent()) text = BLOCKED_INPUT_PLACEHOLDER;
        AgentContentGate gate = contentGate(accountId, unsafe);
        LocalDateTime at = now();
        AgentMessage userMsg = AgentMessage.user("msg_" + uuid(), sessionId, memberId, text, at);
        String assistantId = "msg_" + uuid();
        List<ChatMessage> history = history(sessionId);
        messages.save(userMsg);
        boolean forceRule = !quota.consume(memberId, dailyLimit);
        Kitchen kitchen = kitchens.forMember(memberId);
        AgentOrchestrator.Env env = new AgentOrchestrator.Env(kitchen, new ActionDesk(actionStore, kitchen), gateway, Map.of(),
                gate);
        TurnResult r;
        try {
            r = orchestrator.run(new TurnRequest(sessionId, assistantId, traceId, memberId, text, cmd.context(), history,
                    forceRule), env, sink);
        } catch (RuntimeException e) {
            log.error("咕嘟对话失败 trace={}", traceId, e);
            sink.send("error", Map.of("code", e instanceof BizException b ? b.getCode() : 500,
                    "message", "咕嘟出了点问题，稍后再试", "errorId", traceId));
            traces.save(new AgentTrace(traceId, sessionId, assistantId, memberId, PhoneMasker.mask(text).text(),
                    TraceStatus.ERROR, null, null, AgentPrompts.VERSION, false, 0, null, 0, 0, BigDecimal.ZERO, 0, null,
                    at), List.of());
            return null;
        }
        LocalDateTime done = now();
        messages.save(new AgentMessage(assistantId, sessionId, memberId, "assistant", r.text(), r.tools(), r.cards(),
                r.actions(), traceId, r.reasoning(), null, done));
        for (String old : r.superseded()) {
            actionStore.find(old).ifPresent(a -> messages.updateAction(a.messageId(), a));
        }
        for (int i = 0; i < r.tools().size(); i++) {
            // 口头确认执行了上一轮的卡片：同步那条消息里的动作快照
            if (VerbalConfirm.TOOL.equals(r.tools().get(i).name()) && i < r.toolArgs().size()) {
                actionStore.find(String.valueOf(r.toolArgs().get(i).get("action")))
                        .ifPresent(a -> messages.updateAction(a.messageId(), a));
            }
        }
        PhoneMasker.Masked first = PhoneMasker.mask(text);
        traces.save(new AgentTrace(traceId, sessionId, assistantId, memberId, first.text(), r.status(), r.provider(),
                r.model(), AgentPrompts.VERSION, r.degraded(), r.ms(), r.firstTokenMs(), r.promptTokens(),
                r.completionTokens(), r.costFen(), r.phoneMasked() + first.count(), null, at), r.spans());
        session.touch(text, r.text(), coverOf(r.cards()), done);
        sessions.save(session);
        return r;
    }

    /** 输入结论已在落库前算好（避免重复调用）；输出按登录账号复核。 */
    private AgentContentGate contentGate(Long accountId, Optional<String> inputVerdict) {
        return new AgentContentGate() {
            @Override
            public Optional<String> inputViolation(String text) {
                return inputVerdict;
            }

            @Override
            public String reviewReply(String reply) {
                return contentSecurity.reviewAgentReply(accountId, reply);
            }
        };
    }

    private List<ChatMessage> history(String sessionId) {
        List<ChatMessage> out = new ArrayList<>();
        for (AgentMessage m : messages.recent(sessionId, HISTORY_MESSAGES)) {
            if (m.isAssistant()) {
                String text = HistoryFormatter.assistantText(m.text(), m.cards(), m.actions().stream()
                        .map(a -> new HistoryFormatter.ActionNote(a.tool(), raw(a), a.status().name())).toList());
                out.add(new ChatMessage("assistant", text, null, null, null, m.reasoning()));
            } else {
                out.add(ChatMessage.user(m.text()));
            }
        }
        return out;
    }

    // ================================================================== 动作

    public ConfirmView confirm(Long memberId, String actionId) {
        Kitchen kitchen = kitchens.forMember(memberId);
        ActionDesk desk = new ActionDesk(actionStore, kitchen);
        try {
            ActionDesk.Confirmed c = desk.confirm(memberId, actionId);
            messages.updateAction(c.action().messageId(), c.action());
            return new ConfirmView("DONE", c.result(), c.undoToken());
        } catch (DomainException e) {
            actionStore.find(actionId).filter(a -> a.status() == ActionStatus.EXPIRED)
                    .ifPresent(a -> messages.updateAction(a.messageId(), a));
            throw e;
        }
    }

    public void cancel(Long memberId, String actionId) {
        PendingAction a = new ActionDesk(actionStore, kitchens.forMember(memberId)).cancel(memberId, actionId);
        messages.updateAction(a.messageId(), a);
    }

    public void undo(Long memberId, String undoToken) {
        PendingAction a = new ActionDesk(actionStore, kitchens.forMember(memberId)).undo(memberId, undoToken);
        if (a != null) messages.updateAction(a.messageId(), a);
    }

    /**
     * 修改待确认卡（人份 / 谁吃了 / 实际用量 / 日期…）：合并到原始工具参数后重新跑工具（重新校验归属、重算预览），
     * 旧卡置为 SUPERSEDED，返回新卡。客户端确认的始终是最新预览。
     */
    public ActionView revise(Long memberId, String actionId, Map<String, Object> patch) {
        PendingAction old = actionStore.find(actionId).orElseThrow(() -> new BizException(404, "动作不存在或已过期"));
        Kitchen kitchen = kitchens.forMember(memberId);
        old.assertConfirmable(memberId, kitchen.now());
        AgentTool tool = registry.find(old.tool()).orElseThrow(() -> new BizException("未知的工具"));
        Map<String, Object> merged = mergeRevision(old, patch == null ? Map.of() : patch, kitchen);
        ActionDesk desk = new ActionDesk(actionStore, kitchen);
        ToolOutcome out = tool.run(new ToolContext(kitchen, memberId, old.sessionId(), "", desk.ledger(memberId,
                old.sessionId())), merged);
        if (!out.ok() || out.draft() == null) throw new BizException(out.summary());
        if (!actionStore.transition(actionId, ActionStatus.PENDING, ActionStatus.SUPERSEDED)) {
            throw new BizException("这张卡片已处理过");
        }
        Map<String, Object> args = new LinkedHashMap<>(out.draft().args());
        args.put("_raw", merged);
        ActionDesk.Registered reg = desk.register(memberId, old.sessionId(), old.messageId(), old.tool(),
                new ToolOutcome.Draft(out.draft().title(), args, out.draft().preview()));
        messages.updateAction(old.messageId(), old.withStatus(ActionStatus.SUPERSEDED));
        messages.updateAction(old.messageId(), reg.action());
        return view(reg.action());
    }

    static Map<String, Object> mergeRevision(PendingAction old, Map<String, Object> patch, Kitchen kitchen) {
        Map<String, Object> merged = new LinkedHashMap<>(raw(old));
        for (Map.Entry<String, Object> e : patch.entrySet()) {
            if (!e.getKey().equals("eaterIds") && !e.getKey().equals("lines")) merged.put(e.getKey(), e.getValue());
        }
        if (patch.get("eaterIds") instanceof Collection<?> ids) {
            List<String> names = new ArrayList<>();
            for (Object id : ids) {
                long v = Long.parseLong(String.valueOf(id));
                MemberInfo m = kitchen.members().stream().filter(x -> x.id() == v).findFirst()
                        .orElseThrow(() -> new BizException(403, "成员不属于当前家庭"));
                names.add(m.name());
            }
            merged.put("eaters", names);
        }
        if (MarkCookedTool.NAME.equals(old.tool()) && patch.get("lines") instanceof Collection<?> lines) {
            List<Map<String, Object>> overrides = new ArrayList<>();
            for (Object o : lines) {
                if (!(o instanceof Map<?, ?> l) || l.get("name") == null) continue;
                String status = String.valueOf(l.get("status"));
                Map<String, Object> ov = new LinkedHashMap<>();
                ov.put("name", String.valueOf(l.get("name")));
                switch (status) {
                    case "USED_UP" -> ov.put("amount", "ALL");
                    case "SKIP" -> ov.put("amount", "0");
                    default -> {
                        if (l.get("amount") == null) continue;
                        ov.put("amount", String.valueOf(l.get("amount")));
                        if (l.get("unit") != null) ov.put("unit", String.valueOf(l.get("unit")));
                    }
                }
                overrides.add(ov);
            }
            merged.put("overrides", overrides);
        }
        return merged;
    }

    // ================================================================== 评价 / 建议

    public void feedback(Long memberId, String messageId, int rating, String comment) {
        if (rating != 1 && rating != -1) throw new BizException("rating 只能是 1 或 -1");
        AgentMessage m = messages.findById(messageId).orElseThrow(() -> new BizException(404, "消息不存在"));
        if (!m.memberId().equals(memberId)) throw BizException.forbidden("无权评价这条消息");
        String c = comment == null ? null : comment.length() > 250 ? comment.substring(0, 250) : comment;
        messages.updateRating(messageId, rating, c);
        if (m.traceId() != null) traces.updateFeedback(m.traceId(), rating);
    }

    /**
     * 咕嘟首屏建议。登录账号关闭个性化推荐（A.8）时走 {@link #genericSuggestions()}：
     * 不建厨房上下文（不读成员档案、库存、做饭记录），只给通用问候、时令/大家常做的一道菜和通用快捷提问。
     *
     * @param memberId  当前就餐成员
     * @param accountId 登录账号（偏好按账号存）
     */
    public SuggestionsView suggestions(Long memberId, Long accountId) {
        if (!preferences.personalizedRecommendEnabled(accountId)) return genericSuggestions();
        return suggestions(memberId);
    }

    /** 不针对个人特征的首屏建议。 */
    SuggestionsView genericSuggestions() {
        List<DishCard> picks = homeRecommend.genericPicks(1);
        Map<String, Object> tonight = null;
        if (!picks.isEmpty()) {
            DishCard d = picks.get(0);
            tonight = new LinkedHashMap<>();
            tonight.put("id", d.dishId());
            tonight.put("name", d.name());
            tonight.put("coverUrl", d.coverUrl());
            tonight.put("minutes", d.cookMinutes());
            tonight.put("difficulty", d.difficulty());
            tonight.put("coverage", Map.of("have", 0, "total", 0, "missing", List.of()));
            tonight.put("tags", List.of());
        }
        return new SuggestionsView(greeting(now().getHour()), tonight, List.of(), GENERIC_PROMPTS);
    }

    static final List<String> GENERIC_PROMPTS = List.of("今天吃什么", "下周一周怎么吃", "有什么时令菜", "拍冰箱入库");

    private static String greeting(int h) {
        return h < 11 ? "早上好" : h < 14 ? "中午好" : h < 18 ? "下午好" : "晚上好";
    }

    public SuggestionsView suggestions(Long memberId) {
        Kitchen k = kitchens.forMember(memberId);
        LocalDateTime now = k.now();
        List<MemberInfo> members = k.members();
        String me = members.stream().filter(MemberInfo::me).map(MemberInfo::name).findFirst().orElse("");
        String hello = greeting(now.getHour());
        List<ExpiringView> expiring = k.pantry().stream()
                .filter(p -> p.daysLeft() != null && p.daysLeft() <= 3 && !"OUT".equals(p.status()))
                .sorted((a, b) -> Integer.compare(a.daysLeft(), b.daysLeft())).limit(5)
                .map(p -> new ExpiringView(p.name(), p.iconKey(), p.daysLeft())).toList();
        List<DishInfo> tonight = k.recommendTonight(1, java.util.Set.of());
        List<String> prompts = new ArrayList<>();
        if (!expiring.isEmpty()) prompts.add(expiring.get(0).name() + "快到期了，怎么做？");
        prompts.add("冰箱里的能做什么");
        members.stream().filter(com.gudu.xsd.modules.agent.domain.service.ProfileConstraintPolicy::isBaby).findFirst()
                .ifPresent(b -> prompts.add("给" + b.name() + "安排这周辅食"));
        prompts.add("下周一周怎么吃");
        prompts.add("拍冰箱入库");
        return new SuggestionsView(hello + (me.isEmpty() ? "" : "，" + me),
                tonight.isEmpty() ? null : com.gudu.xsd.modules.agent.application.tools.DishCards.of(tonight.get(0)),
                expiring, prompts.stream().limit(4).toList());
    }

    // ================================================================== 视图

    MessageView view(AgentMessage m) {
        return new MessageView(m.messageId(), m.role(), m.text(),
                m.tools().stream().map(t -> new ToolView(t.name(), t.label(), t.summary(), t.ms(), t.ok())).toList(),
                m.cards(), m.actions().stream().map(this::view).toList(), m.traceId(), m.rating(), m.createdAt());
    }

    ActionView view(PendingAction a) {
        String status = a.status() == ActionStatus.PENDING && a.expiredAt(now()) ? ActionStatus.EXPIRED.name()
                : a.status().name();
        return new ActionView(a.actionId(), a.tool(), a.title(), raw(a), a.preview(), a.expiresAt(), status,
                a.undoToken());
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> raw(PendingAction a) {
        Object r = a.args().get("_raw");
        return r instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    private AgentSession own(Long memberId, String sessionId) {
        AgentSession s = sessions.findById(sessionId).orElseThrow(() -> new BizException(404, "会话不存在"));
        if (!s.ownedBy(memberId)) throw BizException.forbidden("无权访问这个会话");
        return s;
    }

    @SuppressWarnings("unchecked")
    private static String coverOf(List<Map<String, Object>> cards) {
        for (Map<String, Object> c : cards) {
            if (c.get("items") instanceof List<?> items && !items.isEmpty() && items.get(0) instanceof Map<?, ?> d) {
                Object url = ((Map<String, Object>) d).get("coverUrl");
                if (url != null) return String.valueOf(url);
            }
        }
        return null;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private static String uuid() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 20);
    }
}
