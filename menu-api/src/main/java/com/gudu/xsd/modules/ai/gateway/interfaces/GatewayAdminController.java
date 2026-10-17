package com.gudu.xsd.modules.ai.gateway.interfaces;

import cn.dev33.satoken.stp.StpUtil;
import com.gudu.xsd.common.R;
import com.gudu.xsd.common.security.RequireAdmin;
import com.gudu.xsd.modules.ai.gateway.application.GatewayAdminAppService;
import com.gudu.xsd.modules.ai.gateway.application.dto.GatewayDtos.GatewayView;
import com.gudu.xsd.modules.ai.gateway.application.dto.GatewayDtos.ProviderSaveCommand;
import com.gudu.xsd.modules.ai.gateway.application.dto.GatewayDtos.ProviderTestCommand;
import com.gudu.xsd.modules.ai.gateway.application.dto.GatewayDtos.ProviderTestResult;
import com.gudu.xsd.modules.ai.gateway.application.dto.GatewayDtos.ProviderView;
import com.gudu.xsd.modules.ai.gateway.application.dto.GatewayDtos.RouteSaveCommand;
import com.gudu.xsd.modules.ai.gateway.application.dto.GatewayDtos.RouteView;
import com.gudu.xsd.modules.ai.gateway.application.dto.GatewayDtos.UsageView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 模型网关后台（平台超管）：供应商、能力路由、连通性测试、用量。 */
@Tag(name = "模型网关")
@RestController
@RequestMapping("/admin/ai/gateway")
@RequireAdmin
@RequiredArgsConstructor
public class GatewayAdminController {

    private final GatewayAdminAppService svc;

    @Operation(summary = "供应商与路由总览（key 仅掩码）")
    @GetMapping
    public R<GatewayView> overview() {
        return R.ok(svc.overview());
    }

    @Operation(summary = "新增供应商")
    @PostMapping("/providers")
    public R<ProviderView> create(@RequestBody ProviderSaveCommand cmd) {
        return R.ok(svc.createProvider(cmd, operator()));
    }

    @Operation(summary = "修改供应商（apiKey 留空 = 不修改）")
    @PutMapping("/providers/{id}")
    public R<ProviderView> update(@PathVariable Long id, @RequestBody ProviderSaveCommand cmd) {
        return R.ok(svc.updateProvider(id, cmd, operator()));
    }

    @Operation(summary = "删除供应商（被路由引用时 409）")
    @DeleteMapping("/providers/{id}")
    public R<Map<String, Object>> delete(@PathVariable Long id) {
        svc.deleteProvider(id, operator());
        return R.ok(Map.of());
    }

    @Operation(summary = "连通性测试（真实发一次小请求）")
    @PostMapping("/providers/{id}/test")
    public R<ProviderTestResult> test(@PathVariable Long id, @RequestBody(required = false) ProviderTestCommand cmd) {
        return R.ok(svc.testProvider(id, cmd == null ? null : cmd.model(), cmd == null ? null : cmd.capability(),
                operator()));
    }

    @Operation(summary = "修改能力路由（主模型 / 降级链 / 超时 / 重试 / 思考模式）")
    @PutMapping("/routes/{capability}")
    public R<RouteView> route(@PathVariable String capability, @RequestBody RouteSaveCommand cmd) {
        return R.ok(svc.updateRoute(capability, cmd, operator()));
    }

    @Operation(summary = "按能力 / 供应商 / 模型的调用量、失败率、P95、成本")
    @GetMapping("/usage")
    public R<UsageView> usage(@RequestParam(defaultValue = "today") String range) {
        return R.ok(svc.usage(range));
    }

    private static String operator() {
        return "member:" + StpUtil.getLoginIdAsLong();
    }
}
