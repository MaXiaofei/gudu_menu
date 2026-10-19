package com.gudu.xsd.modules.ops.interfaces;

import com.gudu.xsd.common.R;
import com.gudu.xsd.common.security.RequireAdmin;
import com.gudu.xsd.modules.ops.application.OpsStatsAppService;
import com.gudu.xsd.modules.ops.domain.model.OpsStats;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 运营仪表盘（G12）：仅平台超管。 */
@RestController
@RequestMapping("/ops")
@RequiredArgsConstructor
@Tag(name = "运营")
public class OpsStatsController {

    private final OpsStatsAppService appService;

    @GetMapping("/stats")
    @RequireAdmin
    public R<OpsStats> stats(@RequestParam(defaultValue = "14") int days) {
        return R.ok(appService.stats(days));
    }
}
