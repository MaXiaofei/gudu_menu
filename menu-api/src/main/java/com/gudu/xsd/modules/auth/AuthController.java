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
