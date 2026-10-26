package com.gudu.xsd.modules.contentsecurity.interfaces;

import com.gudu.xsd.config.WxProperties;
import com.gudu.xsd.modules.contentsecurity.application.ContentSecurityAppService;
import com.gudu.xsd.modules.contentsecurity.domain.service.WxPushSignature;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;

/**
 * 微信小程序「消息推送」接收（公众平台 → 开发管理 → 消息推送：明文模式 + JSON，Token 与 WX_TOKEN 一致）。
 * <ul>
 *   <li>GET /wx/push：配置时的地址验证，签名通过原样回显 echostr；</li>
 *   <li>POST /wx/push（兼容已配置的 /auth/wx-echo）：事件推送，目前处理 wxa_media_check（图片异步检测结果）。</li>
 * </ul>
 * 微信协议要求回纯文本（success），不包 R（ArchUnit 规则按方法名 wxEcho / wxPush 豁免）。
 */
@Slf4j
@RestController
@Tag(name = "微信消息推送")
public class WxPushController {

    static final String SUCCESS = "success";
    static final String INVALID = "invalid signature";

    private final ContentSecurityAppService contentSecurity;
    private final WxProperties wxProps;
    private final Clock clock;

    public WxPushController(ContentSecurityAppService contentSecurity, WxProperties wxProps, Clock clock) {
        this.contentSecurity = contentSecurity;
        this.wxProps = wxProps;
        this.clock = clock;
    }

    @GetMapping("/wx/push")
    public String wxEcho(@RequestParam String signature, @RequestParam String timestamp,
                         @RequestParam String nonce, @RequestParam String echostr) {
        return WxPushSignature.verify(wxProps.getToken(), timestamp, nonce, signature, clock.instant().getEpochSecond())
                ? echostr : INVALID;
    }

    @PostMapping({"/wx/push", "/auth/wx-echo"})
    public String wxPush(@RequestParam(required = false) String signature,
                         @RequestParam(required = false) String timestamp,
                         @RequestParam(required = false) String nonce,
                         @RequestBody(required = false) String body) {
        return contentSecurity.handlePush(signature, timestamp, nonce, body) ? SUCCESS : INVALID;
    }
}
