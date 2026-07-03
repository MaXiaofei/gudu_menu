package com.gudu.xsd.modules.menu;

import cn.dev33.satoken.stp.StpUtil;
import com.gudu.xsd.common.R;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
@RequiredArgsConstructor
@Tag(name = "做菜")
public class CookController {

    private final CookService cookService;

    /** 整集做：聚合各菜用量 → 扣库存 → 每菜写 cooking_record → 食集标完成。 */
    @PostMapping("/menu/{id}/cook")
    public R<CookResult> cookMenu(@PathVariable Long id) {
        return R.ok(cookService.cookByMenu(id, currentMemberId()));
    }

    /** 单菜直做（轻流程，不入食集）。servings=份数，默认 1。 */
    @PostMapping("/dish/{id}/cook-now")
    public R<CookResult> cookDish(@PathVariable Long id,
                                  @RequestParam(defaultValue = "1") BigDecimal servings) {
        return R.ok(cookService.cookByDish(id, servings, currentMemberId()));
    }

    private Long currentMemberId() {
        try {
            return StpUtil.getLoginIdAsLong();
        } catch (Exception e) {
            return null;   // 未登录（测试上下文）：service 内 memberId 仅用于写 record，可空
        }
    }
}
