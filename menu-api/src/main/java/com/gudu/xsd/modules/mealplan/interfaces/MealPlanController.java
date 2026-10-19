package com.gudu.xsd.modules.mealplan.interfaces;

import cn.dev33.satoken.stp.StpUtil;
import com.gudu.xsd.common.R;
import com.gudu.xsd.modules.mealplan.application.MealPlanAppService;
import com.gudu.xsd.modules.mealplan.application.dto.CreateDraftCommand;
import com.gudu.xsd.modules.mealplan.application.dto.DishCardView;
import com.gudu.xsd.modules.mealplan.application.dto.MealPlanDraftView;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** AI 一周餐程（showcase-ai-community §4）：生成草稿 → 换一道 → 生成食集与采购清单。一周 = 周一至周日。 */
@RestController
@RequestMapping("/mealplan/drafts")
@RequiredArgsConstructor
@Tag(name = "一周餐程")
public class MealPlanController {

    private final MealPlanAppService appService;

    @Data
    public static class ReplaceReq {
        private Long dishId;
    }

    @PostMapping
    public R<MealPlanDraftView> create(@RequestBody(required = false) CreateDraftCommand cmd) {
        return R.ok(appService.create(StpUtil.getLoginIdAsLong(), cmd));
    }

    @GetMapping("/{id}")
    public R<MealPlanDraftView> get(@PathVariable Long id) {
        return R.ok(appService.get(StpUtil.getLoginIdAsLong(), id));
    }

    @PostMapping("/{id}/slots/{slotId}/alternatives")
    public R<Map<String, List<DishCardView>>> alternatives(@PathVariable Long id, @PathVariable Long slotId) {
        return R.ok(Map.of("items", appService.alternatives(StpUtil.getLoginIdAsLong(), id, slotId)));
    }

    @PutMapping("/{id}/slots/{slotId}")
    public R<MealPlanDraftView> replace(@PathVariable Long id, @PathVariable Long slotId, @RequestBody ReplaceReq req) {
        return R.ok(appService.replace(StpUtil.getLoginIdAsLong(), id, slotId, req.getDishId()));
    }

    @PostMapping("/{id}/apply")
    public R<Map<String, Object>> apply(@PathVariable Long id) {
        return R.ok(appService.apply(StpUtil.getLoginIdAsLong(), id));
    }
}
