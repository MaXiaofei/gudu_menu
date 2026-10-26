package com.gudu.xsd.modules.mealplan.interfaces;

import com.gudu.xsd.common.R;
import com.gudu.xsd.modules.mealplan.application.NutritionReportAppService;
import com.gudu.xsd.modules.mealplan.application.dto.NutritionReportView;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 营养报告：成员维度对照档案阈值（range = week 周一至今 / month 1 号至今）。 */
@RestController
@RequiredArgsConstructor
@Tag(name = "营养报告")
public class NutritionReportController {

    private final NutritionReportAppService appService;

    @GetMapping("/nutrition/report")
    public R<NutritionReportView> report(@RequestParam(required = false) Long memberId,
                                         @RequestParam(required = false, defaultValue = "week") String range) {
        if (memberId != null) return R.ok(appService.report(memberId, range));
        Long me;
        try {
            me = com.gudu.xsd.common.security.CurrentMember.id();
        } catch (RuntimeException e) {
            me = null;
        }
        return R.ok(appService.reportForDefaultMember(me, range));
    }
}
