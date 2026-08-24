package com.gudu.xsd.modules.ai;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gudu.xsd.common.PageQuery;
import com.gudu.xsd.common.R;
import com.gudu.xsd.modules.ai.dto.AiProviderInfo;
import com.gudu.xsd.modules.ai.dto.DishEstimateRequest;
import com.gudu.xsd.modules.ai.dto.DishEstimateResponse;
import com.gudu.xsd.modules.ai.dto.MenuCandidate;
import com.gudu.xsd.modules.ai.dto.MenuRecommendRequest;
import com.gudu.xsd.modules.ai.dto.NutritionFillRequest;
import com.gudu.xsd.modules.ai.dto.NutritionFillResponse;
import com.gudu.xsd.modules.member.MpPerm;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    private final AiClientRouter router;
    private final AiCallLogService callLogSvc;

    /** 营养补全：按食材名返回 per100g 6 项指标（可选 ingredientId 落库到该食材）。 */
    @PostMapping("/nutrition/fill")
    @MpPerm("ai.use")
    public R<NutritionFillResponse> fillNutrition(@RequestBody NutritionFillRequest req) {
        return R.ok(svc.fillNutrition(req));
    }

    /** 菜单推荐：基于成员健康约束 + 预算，输出若干组候选菜单。 */
    /** 进页面默认推荐：memberId 可空（无历史走热门兜底），topN 默认 3。 */
    @GetMapping("/menu/recommend/default")
    public R<List<MenuCandidate>> defaultRecommend(
            @RequestParam(required = false) Long memberId,
            @RequestParam(defaultValue = "3") int topN) {
        return R.ok(svc.defaultRecommend(memberId, topN));
    }

    @PostMapping("/menu/recommend")
    @MpPerm("ai.use")
    public R<List<MenuCandidate>> recommendMenu(@RequestBody MenuRecommendRequest req) {
        return R.ok(svc.recommendMenu(req));
    }

    /** 菜品/一餐营养估算：文字描述 → AI 估算该餐总营养（V2 方案2，纯文本）。 */
    @PostMapping("/dish/estimate")
    @MpPerm("ai.use")
    public R<DishEstimateResponse> estimateDish(@RequestBody DishEstimateRequest req) {
        return R.ok(svc.estimateDish(req));
    }

    // ---------------- provider 运行时切换（管理后台用，登录态即可，无 @MpPerm） ----------------

    /** 查询当前 AI provider + 各 provider 就绪状态（key 是否已配）。 */
    @GetMapping("/provider")
    public R<AiProviderInfo> provider() {
        String current = router.currentProvider();
        List<AiProviderInfo.ProviderState> ready = AiClientRouter.PROVIDERS.stream()
                .map(p -> new AiProviderInfo.ProviderState(p, router.providerReady(p)))
                .toList();
        return R.ok(new AiProviderInfo(current, AiClientRouter.PROVIDERS, ready));
    }

    /** 运行时切换 AI provider，写 Redis 立即生效，重启不丢。非法值抛 BizException。 */
    @PutMapping("/provider")
    public R<String> switchProvider(@RequestBody Map<String, String> body) {
        String provider = body.get("provider");
        return R.ok(router.switchProvider(provider));
    }

    // ---------------- Token 用量统计（管理后台用） ----------------

    /**
     * AI 调用日志分页查询。
     * 支持按 scene（nutrition_fill/menu_recommend/dish_estimate）和 status（ok/fail）筛选。
     */
    @GetMapping("/call-log")
    public R<IPage<AiCallLog>> callLog(PageQuery q,
                                       @RequestParam(required = false) String scene,
                                       @RequestParam(required = false) String status) {
        return R.ok(callLogSvc.page(q, scene, status));
    }

    /**
     * Token 用量汇总：按 scene 分组统计调用次数、总 token、失败次数。
     * 可选 days 参数限定统计天数（默认 7 天）。
     */
    @GetMapping("/usage")
    public R<List<Map<String, Object>>> usage(@RequestParam(defaultValue = "7") int days) {
        return R.ok(callLogSvc.usage(days));
    }
}
