package com.gudu.xsd.modules.featureswitch.interfaces;

import cn.dev33.satoken.stp.StpUtil;
import com.gudu.xsd.common.R;
import com.gudu.xsd.common.security.RequireOperator;
import com.gudu.xsd.modules.featureswitch.application.FeatureSwitchAppService;
import com.gudu.xsd.modules.featureswitch.application.FeatureSwitchAppService.SwitchView;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 紧急关停开关管理（AI 六类 + 页面入口三项），权限与厨房圈开关一致（OPERATOR / SUPER_ADMIN）。 */
@RestController
@RequestMapping("/admin/feature-switches")
@RequiredArgsConstructor
@RequireOperator
@Tag(name = "功能开关管理")
public class FeatureSwitchAdminController {

    private final FeatureSwitchAppService service;

    @Data
    public static class SwitchReq {
        /** 开关键，如 agent.chat.enabled。 */
        private String key;
        private Boolean enabled;
        private String reason;
    }

    /** 全部开关的当前状态 + 最近修改记录。 */
    @GetMapping
    public R<List<SwitchView>> list() {
        return R.ok(service.viewAll());
    }

    @Data
    public static class BatchReq {
        /** 端：mp / app。 */
        private String platform;
        private Boolean enabled;
        private String reason;
    }

    /** 某端一键切换全部 AI 类别（如小程序一键关闭全部；关停必须填原因，逐个记日志）。 */
    @PutMapping("/batch")
    public R<List<SwitchView>> setAll(@RequestBody BatchReq req) {
        if (req == null || req.getPlatform() == null || req.getEnabled() == null) return R.fail("platform、enabled 不能为空");
        return R.ok(service.setAll(StpUtil.getLoginIdAsLong(), req.getPlatform(), req.getEnabled(), req.getReason()));
    }

    /** 切换某开关（关停必须填原因；记录操作人与时间；≤15 秒全部实例生效）。 */
    @PutMapping
    public R<SwitchView> set(@RequestBody SwitchReq req) {
        if (req == null || req.getKey() == null || req.getEnabled() == null) return R.fail("key、enabled 不能为空");
        return R.ok(service.set(StpUtil.getLoginIdAsLong(), req.getKey(), req.getEnabled(), req.getReason()));
    }
}
