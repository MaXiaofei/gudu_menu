package com.gudu.xsd.common.security;

/** AI 次数配额的类别（每登录账号每天各算各的）。上限由后台「AI 用量与额度」配置，见 aiguard 模块。 */
public enum AiQuotaCategory {

    /** 拍照识别。 */
    VISION("拍照识别", "今日拍照识别次数已用完，明天再试"),
    /** 一周餐程（含咕嘟对话里的 plan_week 工具）。 */
    MEALPLAN("一周餐程", "今日一周餐程次数已用完，明天再试"),
    /** 咕嘟对话（超限不报错，退回规则回复）。 */
    AGENT("咕嘟对话", "今日咕嘟对话次数已用完，明天再试"),
    /** 其他 AI（营养补全 / 菜单推荐 / 营养估算）。 */
    OTHER("其他 AI", "今日 AI 调用次数已用完，明天再试");

    private final String label;
    private final String exhaustedMessage;

    AiQuotaCategory(String label, String exhaustedMessage) {
        this.label = label;
        this.exhaustedMessage = exhaustedMessage;
    }

    public String label() {
        return label;
    }

    /** 超限时给用户的提示（429）。 */
    public String exhaustedMessage() {
        return exhaustedMessage;
    }
}
