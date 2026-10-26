package com.gudu.xsd.modules.cookbook.interfaces;

import cn.dev33.satoken.stp.StpUtil;
import com.gudu.xsd.common.R;
import com.gudu.xsd.modules.cookbook.application.CookingRecordAppService;
import com.gudu.xsd.modules.cookbook.application.dto.RecentDishView;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 最近做过（G4）：当前登录账号，按菜去重，最近优先。 */
@RestController
@RequestMapping("/cookbook")
@RequiredArgsConstructor
@Tag(name = "菜库")
public class CookbookRecentController {

    private final CookingRecordAppService appService;

    @GetMapping("/recent")
    public R<List<RecentDishView>> recent(@RequestParam(defaultValue = "10") int limit) {
        return R.ok(appService.recent(com.gudu.xsd.common.security.CurrentMember.id(), limit));
    }
}
