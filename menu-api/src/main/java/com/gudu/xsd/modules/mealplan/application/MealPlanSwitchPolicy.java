package com.gudu.xsd.modules.mealplan.application;

import com.gudu.xsd.common.BizException;
import com.gudu.xsd.modules.featureswitch.application.FeatureSwitchAppService;
import com.gudu.xsd.modules.featureswitch.domain.model.FeatureKey;
import com.gudu.xsd.modules.featureswitch.domain.model.Platform;
import org.springframework.stereotype.Component;

/**
 * 一周餐程紧急关停判定（按端）。只拦「生成草稿」（唯一调大模型的入口）；
 * 换一道备选 / 替换 / 查看 / 应用均为规则计算或已有数据，不拦。
 */
@Component
public class MealPlanSwitchPolicy {

    public static final int CODE_MEALPLAN_SUSPENDED = 5035;
    public static final String MSG_MEALPLAN_SUSPENDED = "一周餐程暂停服务";

    private final FeatureSwitchAppService switches;

    public MealPlanSwitchPolicy(FeatureSwitchAppService switches) {
        this.switches = switches;
    }

    /** 该端已关停则抛 5035。 */
    public void requireEnabled(Platform platform) {
        if (!switches.isEnabled(FeatureKey.MEAL_PLAN, platform)) {
            throw new BizException(CODE_MEALPLAN_SUSPENDED, MSG_MEALPLAN_SUSPENDED);
        }
    }
}
