package com.gudu.xsd.modules.aiguard.interfaces;

import cn.dev33.satoken.stp.StpUtil;
import com.gudu.xsd.common.R;
import com.gudu.xsd.common.security.RequireOperator;
import com.gudu.xsd.modules.aiguard.application.AiGuardAdminService;
import com.gudu.xsd.modules.aiguard.application.AiGuardAdminService.Overview;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;

/** AI 用量与额度（全站每日花费上限 + 各类次数上限），权限与功能开关一致（OPERATOR / SUPER_ADMIN）。 */
@RestController
@RequestMapping("/admin/ai-guard")
@RequiredArgsConstructor
@RequireOperator
@Tag(name = "AI 用量与额度")
public class AiGuardAdminController {

    private final AiGuardAdminService service;

    @Data
    public static class UpdateReq {
        /** 全站每日花费上限（元，0 = 不限）。 */
        private BigDecimal dailyBudgetYuan;
        /** 提醒比例（1～100）。 */
        private Integer warnPercent;
        /** 各类每账号每日次数上限：vision / mealplan / agent / other（0 = 不限）。 */
        private Map<String, Integer> limits;
    }

    /** 今日已花费 / 上限 / 百分比 / 状态、各类今日调用次数、当前限额、最近修改日志。 */
    @GetMapping
    public R<Overview> overview() {
        return R.ok(service.overview());
    }

    /** 修改限额（只改传入的字段；记录操作人、时间、新旧值；≤60 秒全部实例生效）。 */
    @PutMapping
    public R<Overview> update(@RequestBody UpdateReq req) {
        if (req == null) return R.fail("没有要修改的内容");
        return R.ok(service.update(StpUtil.getLoginIdAsLong(), req.getDailyBudgetYuan(), req.getWarnPercent(),
                req.getLimits()));
    }
}
