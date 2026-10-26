package com.gudu.xsd.modules.privacy.interfaces;

import cn.dev33.satoken.stp.StpUtil;
import com.gudu.xsd.common.R;
import com.gudu.xsd.modules.privacy.application.ConsentAppService;
import com.gudu.xsd.modules.privacy.application.dto.ConsentStatusView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 敏感个人信息单独同意（B.2）：健康档案 sensitive_health、不满 14 周岁儿童信息 child_info（监护人同意）。
 * 账号一律取登录态；撤回后后端删除该账号提供的对应字段。
 */
@RestController
@RequestMapping("/consent/sensitive")
@RequiredArgsConstructor
@Tag(name = "单独同意")
public class ConsentController {

    private final ConsentAppService consentApp;

    @Operation(summary = "当前账号的单独同意状态")
    @GetMapping
    public R<ConsentStatusView> status() {
        return R.ok(consentApp.status(StpUtil.getLoginIdAsLong()));
    }

    @Operation(summary = "同意（可一次同意多项）")
    @PostMapping
    public R<ConsentStatusView> grant(@RequestBody GrantRequest req, HttpServletRequest http) {
        return R.ok(consentApp.grant(StpUtil.getLoginIdAsLong(), req.getDocTypes(), req.getDocVersion(),
                ClientInfo.channel(req.getChannel(), http), ClientInfo.ip(http)));
    }

    @Operation(summary = "撤回同意（同时删除本账号提供的对应信息）")
    @PostMapping("/withdraw")
    public R<ConsentStatusView> withdraw(@RequestBody WithdrawRequest req, HttpServletRequest http) {
        return R.ok(consentApp.withdraw(StpUtil.getLoginIdAsLong(), req.getDocType(),
                ClientInfo.channel(req.getChannel(), http), ClientInfo.ip(http)));
    }

    @Data
    public static class GrantRequest {
        private List<String> docTypes;
        private String docVersion;
        private String channel;
    }

    @Data
    public static class WithdrawRequest {
        private String docType;
        private String channel;
    }
}
