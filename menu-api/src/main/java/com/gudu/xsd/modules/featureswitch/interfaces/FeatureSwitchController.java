package com.gudu.xsd.modules.featureswitch.interfaces;

import com.gudu.xsd.common.R;
import com.gudu.xsd.modules.featureswitch.application.FeatureSwitchAppService;
import com.gudu.xsd.modules.featureswitch.application.FeatureSwitchAppService.PublicStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 用户侧功能开关只读状态（需登录），客户端据此隐藏咕嘟助手 / 问咕嘟入口。 */
@RestController
@RequestMapping("/feature-switches")
@RequiredArgsConstructor
@Tag(name = "功能开关")
public class FeatureSwitchController {

    private final FeatureSwitchAppService service;

    @Operation(summary = "咕嘟助手 / 问咕嘟是否开放")
    @GetMapping
    public R<PublicStatus> status() {
        return R.ok(service.publicStatus());
    }
}
