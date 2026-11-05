package com.gudu.xsd.modules.featureswitch.interfaces;

import com.gudu.xsd.common.R;
import com.gudu.xsd.common.web.ClientSource;
import com.gudu.xsd.modules.featureswitch.domain.model.Platform;
import com.gudu.xsd.modules.featureswitch.application.FeatureSwitchAppService;
import com.gudu.xsd.modules.featureswitch.application.FeatureSwitchAppService.PublicStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 用户侧功能开关只读状态（免登录，仅 GET，见 SaTokenConfig.GUEST_READ_PATHS；六个 AI 布尔值 + 三个页面入口布尔值），客户端据此隐藏入口、切换协议版本。 */
@RestController
@RequestMapping("/feature-switches")
@RequiredArgsConstructor
@Tag(name = "功能开关")
public class FeatureSwitchController {

    private final FeatureSwitchAppService service;

    @Operation(summary = "生成式 AI 各类别在请求端是否开放（按 X-Client 判端，缺省按 App）")
    @GetMapping
    public R<PublicStatus> status(@RequestHeader(value = ClientSource.HEADER, required = false) String client) {
        return R.ok(service.publicStatus(Platform.fromClientHeader(client)));
    }
}
