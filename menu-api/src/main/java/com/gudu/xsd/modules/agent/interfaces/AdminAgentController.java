package com.gudu.xsd.modules.agent.interfaces;

import com.gudu.xsd.common.R;
import com.gudu.xsd.common.security.RequireAdmin;
import com.gudu.xsd.modules.agent.application.AgentTraceAppService;
import com.gudu.xsd.modules.agent.application.assess.AssessAppService;
import com.gudu.xsd.modules.agent.application.dto.AgentViews.AgentStats;
import com.gudu.xsd.modules.agent.application.dto.AgentViews.MessageView;
import com.gudu.xsd.modules.agent.application.dto.AgentViews.PageView;
import com.gudu.xsd.modules.agent.application.dto.AgentViews.TraceDetail;
import com.gudu.xsd.modules.agent.application.dto.AgentViews.TraceRow;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** 后台：咕嘟调用链与评测（平台超管）。 */
@Tag(name = "咕嘟 Agent · 后台")
@RestController
@RequestMapping("/admin/agent")
@RequireAdmin
@RequiredArgsConstructor
public class AdminAgentController {

    private final AgentTraceAppService traces;
    private final AssessAppService assess;

    @Operation(summary = "调用链列表（rating=-1 只看差评）")
    @GetMapping("/traces")
    public R<PageView<TraceRow>> traces(@RequestParam(required = false) String cursor,
                                        @RequestParam(required = false) Integer size,
                                        @RequestParam(required = false) Integer rating,
                                        @RequestParam(required = false) String status) {
        return R.ok(traces.page(cursor, size, rating, status));
    }

    @Operation(summary = "调用链详情（瀑布图 span）")
    @GetMapping("/traces/{traceId}")
    public R<TraceDetail> trace(@PathVariable String traceId) {
        return R.ok(traces.detail(traceId));
    }

    @Operation(summary = "调用链所在会话的完整对话")
    @GetMapping("/traces/{traceId}/messages")
    public R<List<MessageView>> conversation(@PathVariable String traceId) {
        return R.ok(traces.conversation(traceId));
    }

    @Operation(summary = "当日统计：轮数、工具成功率、P95 首字、成本、护栏拦截、按工具")
    @GetMapping("/stats")
    public R<AgentStats> stats(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return R.ok(traces.stats(date));
    }

    @Operation(summary = "评测用例（按类别统计）")
    @GetMapping("/eval/cases")
    public R<Map<String, Object>> cases(@RequestParam(required = false) String intent) {
        return R.ok(assess.cases(intent));
    }

    @Operation(summary = "新增评测用例（case 导入，或 fromTraceId 差评回流草稿）")
    @PostMapping("/eval/cases")
    public R<Map<String, Object>> createCase(@RequestBody Map<String, Object> body) {
        return R.ok(assess.createCase(body));
    }

    @Operation(summary = "发起评测运行（provider=mock|gateway，后台执行）")
    @PostMapping("/eval/runs")
    public R<Map<String, Object>> run(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> b = body == null ? Map.of() : body;
        return R.ok(assess.startRun(str(b.get("promptVersion")), str(b.get("provider")), str(b.get("split")),
                b.get("runs") instanceof Number n ? n.intValue() : null));
    }

    @Operation(summary = "评测运行列表")
    @GetMapping("/eval/runs")
    public R<List<Map<String, Object>>> runs() {
        return R.ok(assess.runs());
    }

    @Operation(summary = "评测运行的失败用例（含是否基线以来新失败）")
    @GetMapping("/eval/runs/{id}/failures")
    public R<List<Map<String, Object>>> failures(@PathVariable Long id) {
        return R.ok(assess.failures(id));
    }

    @Operation(summary = "设为基线")
    @PostMapping("/eval/runs/{id}/baseline")
    public R<Map<String, Object>> baseline(@PathVariable Long id) {
        assess.markBaseline(id);
        return R.ok(Map.of());
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
