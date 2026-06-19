package com.yanhuo.xsd.modules.ai.impl;

import com.yanhuo.xsd.common.BizException;
import com.yanhuo.xsd.modules.ai.AiClient;
import com.yanhuo.xsd.modules.ai.dto.MenuCandidate;
import com.yanhuo.xsd.modules.ai.dto.MenuRecommendRequest;
import com.yanhuo.xsd.modules.ai.dto.NutritionFillRequest;
import com.yanhuo.xsd.modules.ai.dto.NutritionFillResponse;
import com.yanhuo.xsd.modules.nutrition.IngredientNutrition;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mock AI 客户端：规则表兜底实现。
 *
 * <p>始终装配：作为默认 AiClient（provider=mock 时）+ DeepSeekAiClient 失败时的 fallback bean。
 * 默认 provider 由配置决定（application.yml 的 {@code yanhuo.ai.provider}），DeepSeekAiClient 标
 * {@code @Primary}，provider=deepseek 时 AiService 注入 DeepSeekAiClient，本类仍作为降级依赖可用。
 *
 * <p>营养补全：先查关键词精确表（参考中国食物成分表 per100g），未命中走分类兜底（按名字含「肉/蛋/奶/菜/米/油」匹配模板），
 * 全无匹配抛 {@link BizException}。菜单推荐：仅占位，真正编排由 AiService 调 MenuRecommender 完成；
 * AiClient 层只负责「外部 AI 能力」，mock 下推荐返回空（实际推荐逻辑是确定性算法，走 AiService 内的 MenuRecommender）。
 */
@Component
public class MockAiClient implements AiClient {

    private static final String SOURCE = "mock";

    /** 关键词表：[cal, protein, fat, carb, sugar, gi]，per 100g。 */
    private static final Map<String, double[]> TABLE = Map.ofEntries(
            Map.entry("猪肉", new double[]{143, 20.3, 6.2, 1.5, 0.9, 0}),
            Map.entry("牛肉", new double[]{125, 20.2, 4.2, 1.2, 0.6, 0}),
            Map.entry("鸡胸", new double[]{133, 19.4, 5.0, 2.5, 0, 0}),
            Map.entry("鸡蛋", new double[]{144, 13.3, 8.8, 2.8, 1.5, 30}),
            Map.entry("虾", new double[]{48, 10.4, 0.7, 0, 0, 0}),
            Map.entry("草鱼", new double[]{113, 16.6, 5.2, 0, 0, 0}),
            Map.entry("豆腐", new double[]{98, 12.2, 4.8, 1.5, 0.5, 15}),
            Map.entry("番茄", new double[]{19, 0.9, 0.2, 4.0, 2.6, 30}),
            Map.entry("西红柿", new double[]{19, 0.9, 0.2, 4.0, 2.6, 30}),
            Map.entry("土豆", new double[]{77, 2.0, 0.2, 17.2, 0.8, 78}),
            Map.entry("黄瓜", new double[]{16, 0.8, 0.2, 2.9, 2.3, 15}),
            Map.entry("白菜", new double[]{20, 1.5, 0.1, 3.4, 1.7, 15}),
            Map.entry("米饭", new double[]{116, 2.6, 0.3, 25.9, 0, 83}),
            Map.entry("面条", new double[]{280, 8.3, 0.7, 61, 0, 82}),
            Map.entry("牛奶", new double[]{54, 3.0, 3.2, 3.4, 0, 27}),
            Map.entry("苹果", new double[]{52, 0.3, 0.2, 13.8, 10.4, 36})
    );

    /** 分类兜底模板：[cal, protein, fat, carb, sugar, gi]。 */
    private static final double[] TPL_MEAT = {140, 20, 6, 1, 0, 0};      // 含 肉/鱼/虾/鸡/猪/牛/羊
    private static final double[] TPL_EGG = {144, 13, 9, 3, 1, 30};      // 含 蛋
    private static final double[] TPL_MILK = {54, 3, 3, 3, 0, 27};       // 含 奶/乳
    private static final double[] TPL_VEG = {25, 1.5, 0.2, 4, 2, 15};    // 含 菜/瓜/茄/椒/菇/菠/芹/韭
    private static final double[] TPL_STAPLE = {300, 8, 1, 60, 0, 80};   // 含 米/面/粉/麦/谷/粥/饭
    private static final double[] TPL_OIL = {899, 0, 99, 0, 0, 0};       // 含 油

    @Override
    public NutritionFillResponse fillNutrition(NutritionFillRequest req) {
        String name = req.name();
        if (name == null || name.isBlank()) {
            throw new BizException("食材名不能为空");
        }
        double[] v = TABLE.get(name);
        if (v == null) {
            v = fallback(name);
        }
        return new NutritionFillResponse(toNutritionList(v), SOURCE);
    }

    @Override
    public List<MenuCandidate> recommendMenu(MenuRecommendRequest req) {
        // mock 下推荐走确定性算法（AiService 编排 MenuRecommender），AiClient 层只表示外部 AI 能力。
        // 此处返回空，真正的推荐候选由 AiService 直接产出。
        return List.of();
    }

    @Override
    public String provider() {
        return SOURCE;
    }

    // ---------------- 内部 ----------------

    /** 分类兜底：按名字子串匹配模板；全无匹配抛 BizException。 */
    private static double[] fallback(String name) {
        if (containsAny(name, "肉", "鱼", "虾", "鸡", "猪", "牛", "羊")) return TPL_MEAT;
        if (name.contains("蛋")) return TPL_EGG;
        if (name.contains("奶") || name.contains("乳")) return TPL_MILK;
        if (containsAny(name, "菜", "瓜", "茄", "椒", "菇", "菠", "芹", "韭")) return TPL_VEG;
        if (containsAny(name, "米", "面", "粉", "麦", "谷", "粥", "饭")) return TPL_STAPLE;
        if (name.contains("油")) return TPL_OIL;
        throw new BizException("Mock 无法识别食材「" + name + "」，请改用 GLM 或手动填写营养");
    }

    private static boolean containsAny(String s, String... subs) {
        for (String sub : subs) {
            if (s.contains(sub)) return true;
        }
        return false;
    }

    /** [cal,protein,fat,carb,sugar,gi] -> List<IngredientNutrition>(metricId 1..6)。 */
    private static List<IngredientNutrition> toNutritionList(double[] v) {
        List<IngredientNutrition> list = new ArrayList<>(6);
        for (int i = 0; i < 6; i++) {
            IngredientNutrition n = new IngredientNutrition();
            n.setMetricId((long) (i + 1));
            n.setValue(BigDecimal.valueOf(v[i]).stripTrailingZeros());
            list.add(n);
        }
        return list;
    }
}
