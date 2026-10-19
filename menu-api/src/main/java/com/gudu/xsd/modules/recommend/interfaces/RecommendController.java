package com.gudu.xsd.modules.recommend.interfaces;

import cn.dev33.satoken.stp.StpUtil;
import com.gudu.xsd.common.R;
import com.gudu.xsd.modules.recommend.application.HomeRecommendAppService;
import com.gudu.xsd.modules.recommend.application.dto.HomeRecommendView;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 首页智荐（G5，需登录）。 */
@RestController
@RequestMapping("/recommend")
@RequiredArgsConstructor
@Tag(name = "智荐")
public class RecommendController {

    private final HomeRecommendAppService appService;

    @GetMapping("/home")
    public R<HomeRecommendView> home() {
        return R.ok(appService.home(StpUtil.getLoginIdAsLong()));
    }
}
