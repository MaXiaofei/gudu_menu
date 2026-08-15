package com.gudu.xsd.modules.auth;

import cn.dev33.satoken.stp.StpUtil;
import com.gudu.xsd.common.R;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "鉴权")
public class AuthController {

    private final AuthService authService;

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
