package com.gudu.xsd.modules.consent.interfaces;

import cn.dev33.satoken.stp.StpUtil;
import com.gudu.xsd.common.BizException;
import com.gudu.xsd.common.R;
import com.gudu.xsd.modules.consent.application.ConsentAppService;
import com.gudu.xsd.modules.consent.application.dto.ConsentView;
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
 * 协议同意上报（mini-release-compliance B.1）：登录成功后由客户端上报登录页勾选的协议版本；撤回传 granted=false。
 * 账号取登录态，客户端 IP 取反向代理头（X-Forwarded-For 首个 / X-Real-IP），没有则取连接地址。
 */
@RestController
@RequestMapping("/consent")
@RequiredArgsConstructor
@Tag(name = "协议同意")
public class ConsentController {

    private final ConsentAppService consents;

    @Data
    public static class ConsentReq {
        /** TERMS / PRIVACY / HEALTH / MINOR；为空 = TERMS + PRIVACY。 */
        private List<String> docTypes;
        /** 协议版本（客户端 LEGAL_VERSION）。 */
        private String docVersion;
        /** true 同意（默认）/ false 撤回。 */
        private Boolean granted;
        /** mp-weixin / app / h5；为空时取 X-Client 请求头。 */
        private String channel;
    }

    @Operation(summary = "上报协议同意 / 撤回 {docTypes?, docVersion, granted?, channel?}")
    @PostMapping
    public R<List<ConsentView>> record(@RequestBody ConsentReq req, HttpServletRequest http) {
        if (req == null || req.getDocVersion() == null || req.getDocVersion().isBlank()) {
            throw new BizException("缺少协议版本");
        }
        if (req.getDocTypes() != null && req.getDocTypes().size() > 4) throw new BizException("协议类型过多");
        String channel = req.getChannel() != null ? req.getChannel() : http.getHeader("X-Client");
        return R.ok(consents.record(StpUtil.getLoginIdAsLong(), req.getDocTypes(), req.getDocVersion(),
                !Boolean.FALSE.equals(req.getGranted()), channel, clientIp(http)));
    }

    @Operation(summary = "我的同意记录")
    @GetMapping("/me")
    public R<List<ConsentView>> mine() {
        return R.ok(consents.history(StpUtil.getLoginIdAsLong()).stream()
                .map(v -> new ConsentView(v.id(), v.accountId(), v.docType(), v.docVersion(), v.granted(),
                        v.channel(), null, v.createdAt()))
                .toList());
    }

    static String clientIp(HttpServletRequest http) {
        String xff = http.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        String real = http.getHeader("X-Real-IP");
        if (real != null && !real.isBlank()) return real.trim();
        return http.getRemoteAddr();
    }
}
