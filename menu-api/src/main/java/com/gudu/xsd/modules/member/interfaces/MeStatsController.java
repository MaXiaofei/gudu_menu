package com.gudu.xsd.modules.member.interfaces;

import com.gudu.xsd.common.R;
import com.gudu.xsd.modules.member.application.MeStatsAppService;
import com.gudu.xsd.modules.member.application.MeStatsView;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 「我的」页统计：GET /me/stats（需登录）。 */
@RestController
@RequestMapping("/me")
@RequiredArgsConstructor
@Tag(name = "家庭成员")
public class MeStatsController {

    private final MeStatsAppService appService;

    @GetMapping("/stats")
    public R<MeStatsView> stats() {
        return R.ok(appService.stats());
    }
}
