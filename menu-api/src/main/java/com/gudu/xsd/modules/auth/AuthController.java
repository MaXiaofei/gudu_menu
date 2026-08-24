package com.gudu.xsd.modules.auth;

import cn.dev33.satoken.stp.StpUtil;
import com.gudu.xsd.common.R;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "鉴权")
public class AuthController {

    private final AuthService authService;
    private final com.gudu.xsd.config.WxProperties wxProps;

    @PostMapping("/login")
    public R<Map<String, Object>> login(@RequestBody @Valid LoginDTO dto) {
        return R.ok(authService.login(dto));
    }

    /** 微信小程序静默登录：{code}（wx.login 换取，一次性 5 分钟有效）→ {token, nickname}。 */
    @PostMapping("/wx-login")
    public R<Map<String, Object>> wxLogin(@RequestBody Map<String, String> body) {
        String code = body == null ? null : body.get("code");
        if (code == null || code.isBlank()) throw new com.gudu.xsd.common.BizException("code 不能为空");
        return R.ok(authService.wxLogin(code));
    }

    /**
     * 微信公众平台「开发设置→服务器配置→服务器地址」验证接口。
     * 微信服务器发 GET：signature / timestamp / nonce / echostr；
     * 校验 signature = SHA1(字典序排序(token, timestamp, nonce)) 后原样回显 echostr。
     * URL 填：https://imxf.cloud/gudu/auth/wx-echo（Token 与环境变量 WX_TOKEN 一致）。
     */
    @GetMapping("/wx-echo")
    public String wxEcho(@RequestParam String signature,
                         @RequestParam String timestamp,
                         @RequestParam String nonce,
                         @RequestParam String echostr) {
        String expected = sha1(Arrays.stream(new String[]{wxProps.getToken(), timestamp, nonce})
                .sorted().collect(Collectors.joining()));
        if (!expected.equals(signature)) {
            throw new com.gudu.xsd.common.BizException("签名校验失败");
        }
        return echostr;
    }

    private static String sha1(String s) {
        try {
            var md = java.security.MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            var sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @PostMapping("/logout")
    public R<?> logout() {
        StpUtil.logout();
        return R.ok(null);
    }

    @GetMapping("/me")
    public R<?> me() {
        return R.ok(StpUtil.getLoginIdAsLong());
    }
}
