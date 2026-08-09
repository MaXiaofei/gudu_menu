package com.gudu.xsd.modules.menu;

import cn.dev33.satoken.stp.StpUtil;
import com.gudu.xsd.common.R;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 做菜确认接口（V42 手动库存版）。
 *
 * <p>点「开始做饭」→ GET /menu/{id}/cook-materials（弹窗数据，不落库）
 * → 用户确认 → POST /menu/{id}/cook（body: usedUp/partiallyUsed）→ 更新档位 + 写食记 + 食集完成。
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "做菜")
public class CookController {

    private final CookService cookService;

    /** 做菜确认请求：usedUp=用完了的食材，partiallyUsed=用了一些的食材。 */
    @Data
    public static class CookReq {
        private List<Long> usedUp;
        private List<Long> partiallyUsed;
    }

    /** 确认弹窗数据：本次用到的食材 + 当前档位 + 是否调料（不落库）。 */
    @GetMapping("/menu/{id}/cook-materials")
    public R<CookMaterialsVO> cookMaterials(@PathVariable Long id) {
        return R.ok(cookService.cookMaterials(id));
    }

    /** 整集做菜确认：按 usedUp/partiallyUsed 更新档位 → 写食记 → 食集标完成。 */
    @PostMapping("/menu/{id}/cook")
    public R<CookResult> cookMenu(@PathVariable Long id, @RequestBody(required = false) CookReq req) {
        List<Long> usedUp = req == null ? null : req.getUsedUp();
        List<Long> partiallyUsed = req == null ? null : req.getPartiallyUsed();
        return R.ok(cookService.cookByMenu(id, currentMemberId(), usedUp, partiallyUsed));
    }

    private Long currentMemberId() {
        try {
            return StpUtil.getLoginIdAsLong();
        } catch (Exception e) {
            return null;   // 未登录（测试上下文）：service 内 memberId 仅用于写 record，可空
        }
    }
}
