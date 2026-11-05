package com.gudu.xsd.modules.featureswitch.domain.model;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * 紧急关停开关的类别（每个类别按端各有一个 sys_switch 行）。
 * 库里的 switch_key = 类别基础键 + "." + 端（mp / app），如 {@code agent.chat.enabled.mp}。
 *
 * <p>{@link Group#AI}：生成式 AI，只用于出现内容风险 / 审核要求时一键关闭；小程序默认开，App 默认关。
 * {@link Group#ENTRY}：页面入口，与 AI 独立分组；功能已在运行，默认两端都开，关停时由客户端隐藏入口。
 * 厨房圈开关（community.enabled）沿用 community 模块的原实现，不在此列。
 */
public enum FeatureKey {

    /** 咕嘟助手（咕嘟页与全部对话入口）。 */
    AGENT_CHAT("agent.chat.enabled", "agentChat", "咕嘟助手", Group.AI),
    /** 问咕嘟（跟做模式里的提问）。 */
    AGENT_COOK_ASK("agent.cook_ask.enabled", "cookAsk", "问咕嘟", Group.AI),
    /** 拍照识别（创建识别任务）。 */
    VISION("ai.vision.enabled", "vision", "拍照识别", Group.AI),
    /** 一周餐程（AI 生成草稿）。 */
    MEAL_PLAN("ai.mealplan.enabled", "mealPlan", "一周餐程", Group.AI),
    /** 营养估算（/ai/dish/estimate 一餐估算、/ai/nutrition/fill 食材营养补全；管理后台运营维护食材不受限）。 */
    ESTIMATE("ai.estimate.enabled", "estimate", "营养估算", Group.AI),
    /** AI 帮写（厨房圈发帖文案生成）。 */
    POST_TEXT("ai.post_text.enabled", "postText", "AI 帮写", Group.AI),

    /** 写菜谱（菜谱页「写菜谱」按钮与「我的 → 写菜谱」入口）。 */
    RECIPE_WRITE("entry.recipe_write.enabled", "recipeWrite", "写菜谱", Group.ENTRY),
    /** 草稿箱（「我的 → 草稿箱」入口与草稿箱页）。 */
    DISH_DRAFTS("entry.dish_drafts.enabled", "dishDrafts", "草稿箱", Group.ENTRY),
    /** 采购清单分享（采购清单详情的「分享」按钮）。 */
    SHOPPING_SHARE("entry.shopping_share.enabled", "shoppingShare", "采购清单分享", Group.ENTRY);

    /** 开关分组：管理后台分开展示，一键关闭全部 AI 只作用于 AI 组。 */
    public enum Group {
        AI, ENTRY
    }

    private final String baseKey;
    private final String category;
    private final String label;
    private final Group group;

    FeatureKey(String baseKey, String category, String label, Group group) {
        this.baseKey = baseKey;
        this.category = category;
        this.label = label;
        this.group = group;
    }

    public Group group() {
        return group;
    }

    /** 该类别在该端未配置时的默认状态：页面入口默认开；AI 沿用端默认（小程序开、App 关）。 */
    public boolean defaultEnabled(Platform platform) {
        return group == Group.ENTRY || platform.defaultEnabled();
    }

    public static List<FeatureKey> aiKeys() {
        return Arrays.stream(values()).filter(k -> k.group == Group.AI).toList();
    }

    public static List<FeatureKey> entryKeys() {
        return Arrays.stream(values()).filter(k -> k.group == Group.ENTRY).toList();
    }

    /** 类别基础键（不含端后缀）。 */
    public String baseKey() {
        return baseKey;
    }

    /** sys_switch.switch_key：基础键 + 端后缀。 */
    public String key(Platform platform) {
        return baseKey + "." + platform.code();
    }

    /** 对外类别名（与用户侧接口字段一致）：agentChat / cookAsk / vision / mealPlan / estimate / postText。 */
    public String category() {
        return category;
    }

    public String label() {
        return label;
    }

    /** 由完整 switch_key（带端后缀）反查出的类别与端。 */
    public record Resolved(FeatureKey feature, Platform platform) {
    }

    public static Optional<Resolved> resolve(String switchKey) {
        if (switchKey == null) return Optional.empty();
        for (FeatureKey f : values()) {
            for (Platform p : Platform.values()) {
                if (f.key(p).equals(switchKey)) return Optional.of(new Resolved(f, p));
            }
        }
        return Optional.empty();
    }

    public static Optional<FeatureKey> ofBaseKey(String baseKey) {
        return Arrays.stream(values()).filter(k -> k.baseKey.equals(baseKey)).findFirst();
    }
}
