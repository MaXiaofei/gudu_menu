package com.gudu.xsd.modules.privacy.interfaces;

import cn.dev33.satoken.stp.StpUtil;
import com.gudu.xsd.common.BizException;
import com.gudu.xsd.common.R;
import com.gudu.xsd.modules.privacy.application.ConsentAppService;
import com.gudu.xsd.modules.privacy.application.ConsentAppService.ConsentHistoryView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 登录协议同意上报（mini-release-compliance B.1）：小程序登录页勾选《用户协议》《隐私政策》、登录成功后上报
 * 当前 LEGAL_VERSION；撤回传 granted=false。账号取登录态，渠道取 body.channel 或 X-Client 头，IP 取反代头。
 * 健康档案 / 儿童信息的单独同意见 {@link ConsentController}（/consent/sensitive）。
 */
@RestController
@RequestMapping("/consent")
@RequiredArgsConstructor
@Tag(name = "协议同意")
public class LegalConsentController {

    private final ConsentAppService consentApp;

    @Data
    public static class LegalConsentReq {
        /** terms / privacy；为空 = 两份都记。 */
        private List<String> docTypes;
        /** 协议版本（客户端 LEGAL_VERSION）。 */
        private String docVersion;
        /** true 同意（默认）/ false 撤回。 */
        private Boolean granted;
        /** mp-weixin / app / web；为空取 X-Client。 */
        private String channel;
    }

    @Operation(summary = "上报登录协议同意 / 撤回 {docTypes?, docVersion, granted?, channel?}")
    @PostMapping
    public R<List<ConsentHistoryView>> record(@RequestBody LegalConsentReq req, HttpServletRequest http) {
        if (req == null) throw new BizException("缺少协议版本");
        if (req.getDocTypes() != null && req.getDocTypes().size() > 4) throw new BizException("协议类型过多");
        return R.ok(consentApp.recordLegal(StpUtil.getLoginIdAsLong(), req.getDocTypes(), req.getDocVersion(),
                !Boolean.FALSE.equals(req.getGranted()), ClientInfo.channel(req.getChannel(), http), ClientInfo.ip(http)));
    }
}
