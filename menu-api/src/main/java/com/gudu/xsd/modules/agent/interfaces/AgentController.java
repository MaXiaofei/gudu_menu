package com.gudu.xsd.modules.agent.interfaces;

import cn.dev33.satoken.stp.StpUtil;
import com.gudu.xsd.common.R;
import com.gudu.xsd.modules.agent.application.AgentAppService;
import com.gudu.xsd.modules.agent.application.AgentEventSink;
import com.gudu.xsd.modules.agent.application.dto.AgentViews.ActionView;
import com.gudu.xsd.modules.agent.application.dto.AgentViews.ConfirmView;
import com.gudu.xsd.modules.agent.application.dto.AgentViews.CreateSessionCommand;
import com.gudu.xsd.modules.agent.application.dto.AgentViews.FeedbackCommand;
import com.gudu.xsd.modules.agent.application.dto.AgentViews.MessageView;
import com.gudu.xsd.modules.agent.application.dto.AgentViews.PageView;
import com.gudu.xsd.modules.agent.application.dto.AgentViews.ReviseCommand;
import com.gudu.xsd.modules.agent.application.dto.AgentViews.SendCommand;
import com.gudu.xsd.modules.agent.application.dto.AgentViews.SessionCreated;
import com.gudu.xsd.modules.agent.application.dto.AgentViews.SessionView;
import com.gudu.xsd.modules.agent.application.dto.AgentViews.StatusView;
import com.gudu.xsd.modules.agent.application.dto.AgentViews.SuggestionsView;
import com.gudu.xsd.modules.agent.application.dto.AgentViews.UndoCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.Executor;

/** 咕嘟 Agent（contracts.md §1）。发消息为 SSE，其余统一 R。 */
@Slf4j
@Tag(name = "咕嘟 Agent")
@RestController
@RequestMapping("/agent")
public class AgentController {

    static final long SSE_TIMEOUT_MS = 180_000L;

    private final AgentAppService svc;
    private final Executor executor;

    public AgentController(AgentAppService svc, @Qualifier("agentExecutor") Executor executor) {
        this.svc = svc;
        this.executor = executor;
    }

    @Operation(summary = "新建会话")
    @PostMapping("/sessions")
    public R<SessionCreated> create(@RequestBody(required = false) CreateSessionCommand cmd) {
        return R.ok(new SessionCreated(svc.createSession(me(), cmd == null ? null : cmd.title())));
    }

    @Operation(summary = "最近会话（游标分页）")
    @GetMapping("/sessions")
    public R<PageView<SessionView>> sessions(@RequestParam(required = false) String cursor,
                                             @RequestParam(required = false) Integer size) {
        return R.ok(svc.sessions(me(), cursor, size));
    }

    @Operation(summary = "会话消息（倒序，游标分页）")
    @GetMapping("/sessions/{id}/messages")
    public R<PageView<MessageView>> messages(@PathVariable String id, @RequestParam(required = false) String cursor,
                                             @RequestParam(required = false) Integer size) {
        return R.ok(svc.messages(me(), id, cursor, size));
    }

    /**
     * 发消息（SSE）：meta → tool_start / tool_end / card / action → token → done；失败 error。
     * 协议要求返回事件流，不包 R（ArchUnit 规则按方法名豁免）。
     */
    @Operation(summary = "发消息（SSE 事件流）")
    @PostMapping(value = "/sessions/{id}/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamMessage(@PathVariable String id, @RequestBody SendCommand cmd) {
        Long member = me();
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        AgentEventSink sink = (event, data) -> {
            try {
                emitter.send(SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON));
            } catch (IOException | IllegalStateException e) {
                log.debug("SSE 客户端已断开: {}", e.getMessage());
            }
        };
        // 子线程恢复本次请求的租户上下文（家庭域表访问必须有 TenantContext，否则拒绝）
        executor.execute(com.gudu.xsd.common.tenant.TenantContextHolder.wrap(() -> {
            try {
                svc.send(member, id, cmd, sink);
            } catch (RuntimeException e) {
                sink.send("error", Map.of("code", 1, "message", e.getMessage() == null ? "出错了" : e.getMessage(),
                        "errorId", ""));
            } finally {
                emitter.complete();
            }
        }));
        return emitter;
    }

    @Operation(summary = "确认待确认动作（执行写操作，返回撤销凭据）")
    @PostMapping("/actions/{actionId}/confirm")
    public R<ConfirmView> confirm(@PathVariable String actionId) {
        return R.ok(svc.confirm(me(), actionId));
    }

    @Operation(summary = "取消待确认动作")
    @PostMapping("/actions/{actionId}/cancel")
    public R<StatusView> cancel(@PathVariable String actionId) {
        svc.cancel(me(), actionId);
        return R.ok(new StatusView("CANCELLED"));
    }

    @Operation(summary = "修改待确认卡（人份 / 谁吃了 / 实际用量 / 日期），返回新卡")
    @PostMapping("/actions/{actionId}/revise")
    public R<ActionView> revise(@PathVariable String actionId, @RequestBody ReviseCommand cmd) {
        return R.ok(svc.revise(me(), actionId, cmd == null ? Map.of() : cmd.args()));
    }

    @Operation(summary = "撤销已执行的动作（5 分钟内）")
    @PostMapping("/actions/undo")
    public R<StatusView> undo(@RequestBody UndoCommand cmd) {
        svc.undo(me(), cmd.undoToken());
        return R.ok(new StatusView("UNDONE"));
    }

    @Operation(summary = "评价一条回复（1 / -1）")
    @PostMapping("/messages/{messageId}/feedback")
    public R<Map<String, Object>> feedback(@PathVariable String messageId, @RequestBody FeedbackCommand cmd) {
        svc.feedback(me(), messageId, cmd.rating() == null ? 0 : cmd.rating(), cmd.comment());
        return R.ok(Map.of());
    }

    @Operation(summary = "咕嘟首屏：问候、今晚推荐、快到期、快捷提问")
    @GetMapping("/suggestions")
    public R<SuggestionsView> suggestions() {
        return R.ok(svc.suggestions(me()));
    }

    private static Long me() {
        return com.gudu.xsd.common.security.CurrentMember.id();
    }
}
