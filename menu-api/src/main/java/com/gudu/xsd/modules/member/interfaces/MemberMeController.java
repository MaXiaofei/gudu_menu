package com.gudu.xsd.modules.member.interfaces;

import cn.dev33.satoken.stp.StpUtil;
import com.gudu.xsd.common.R;
import com.gudu.xsd.modules.member.application.MemberMeView;
import com.gudu.xsd.modules.member.application.MemberProfileAppService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 个人资料（G7）：GET/PUT /member/me，身份取登录账号。 */
@RestController
@RequestMapping("/member")
@RequiredArgsConstructor
@Tag(name = "家庭成员")
public class MemberMeController {

    private final MemberProfileAppService appService;

    @Data
    public static class UpdateMeReq {
        private String name;
        private String avatarUrl;
    }

    @GetMapping("/me")
    public R<MemberMeView> me() {
        return R.ok(appService.me(StpUtil.getLoginIdAsLong()));
    }

    @PutMapping("/me")
    public R<MemberMeView> update(@RequestBody UpdateMeReq req) {
        return R.ok(appService.update(StpUtil.getLoginIdAsLong(), req.getName(), req.getAvatarUrl()));
    }
}
