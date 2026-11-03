package com.gudu.xsd.modules.ops.interfaces;

import com.gudu.xsd.common.R;
import com.gudu.xsd.common.security.RequireOperator;
import com.gudu.xsd.modules.ops.application.OpsHealthAppService;
import com.gudu.xsd.modules.ops.domain.model.HealthReport;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 系统健康摘要：平台运营及超管可读（应用 / MySQL / Redis / 向量库 / AI）。 */
@RestController
@RequestMapping("/ops")
@RequiredArgsConstructor
@Tag(name = "运营")
public class OpsHealthController {

    private final OpsHealthAppService appService;

    @GetMapping("/health")
    @RequireOperator
    public R<HealthReport> health() {
        return R.ok(appService.check());
    }
}
