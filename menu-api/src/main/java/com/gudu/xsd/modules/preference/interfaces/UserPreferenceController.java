package com.gudu.xsd.modules.preference.interfaces;

import cn.dev33.satoken.stp.StpUtil;
import com.gudu.xsd.common.R;
import com.gudu.xsd.modules.preference.application.PreferenceView;
import com.gudu.xsd.modules.preference.application.UserPreferenceAppService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 用户偏好（A.8 个性化推荐开关，按登录账号）。 */
@RestController
@RequestMapping("/preference")
@RequiredArgsConstructor
@Tag(name = "用户偏好")
public class UserPreferenceController {

    private final UserPreferenceAppService appService;

    @Data
    public static class UpdateReq {
        private Boolean personalizedRecommend;
    }

    @GetMapping
    public R<PreferenceView> get() {
        return R.ok(appService.get(StpUtil.getLoginIdAsLong()));
    }

    @PutMapping
    public R<PreferenceView> update(@RequestBody UpdateReq req) {
        return R.ok(appService.update(StpUtil.getLoginIdAsLong(), req.getPersonalizedRecommend()));
    }
}
