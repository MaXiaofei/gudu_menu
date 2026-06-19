package com.yanhuo.xsd.modules.ai;

import com.yanhuo.xsd.common.R;
import com.yanhuo.xsd.modules.ai.dto.MenuCandidate;
import com.yanhuo.xsd.modules.ai.dto.MenuRecommendRequest;
import com.yanhuo.xsd.modules.ai.dto.NutritionFillRequest;
import com.yanhuo.xsd.modules.ai.dto.NutritionFillResponse;
import com.yanhuo.xsd.modules.member.MpPerm;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AI 能力接口：营养补全 + 菜单推荐。挂 {@code @MpPerm("ai.use")}。
 * 范式照 PantryController / DishController：返回 R<T>，@Tag 分组。
 */
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
@Tag(name = "AI")
public class AiController {

    private final AiService svc;

    /** 营养补全：按食材名返回 per100g 6 项指标（可选 ingredientId 落库到该食材）。 */
    @PostMapping("/nutrition/fill")
    @MpPerm("ai.use")
    public R<NutritionFillResponse> fillNutrition(@RequestBody NutritionFillRequest req) {
        return R.ok(svc.fillNutrition(req));
    }

    /** 菜单推荐：基于成员健康约束 + 预算，输出若干组候选菜单。 */
    @PostMapping("/menu/recommend")
    @MpPerm("ai.use")
    public R<List<MenuCandidate>> recommendMenu(@RequestBody MenuRecommendRequest req) {
        return R.ok(svc.recommendMenu(req));
    }
}
