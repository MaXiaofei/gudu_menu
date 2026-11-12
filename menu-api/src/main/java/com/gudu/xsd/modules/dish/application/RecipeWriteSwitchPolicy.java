package com.gudu.xsd.modules.dish.application;

import com.gudu.xsd.common.BizException;
import com.gudu.xsd.modules.featureswitch.application.FeatureSwitchAppService;
import com.gudu.xsd.modules.featureswitch.domain.model.FeatureKey;
import com.gudu.xsd.modules.featureswitch.domain.model.Platform;
import org.springframework.stereotype.Component;

/**
 * 写菜谱紧急关停判定（按端）：新建 / 更新 / 链接导入 / 存草稿统一拦截。
 * 只拦写入，查看已有菜谱、做菜、加入食集等不受影响。
 */
@Component
public class RecipeWriteSwitchPolicy {

    public static final int CODE_RECIPE_WRITE_SUSPENDED = 5038;
    public static final String MSG_RECIPE_WRITE_SUSPENDED = "写菜谱暂停服务";

    private final FeatureSwitchAppService switches;

    public RecipeWriteSwitchPolicy(FeatureSwitchAppService switches) {
        this.switches = switches;
    }

    /** 该端已关停则抛 5038。 */
    public void requireEnabled(Platform platform) {
        if (!switches.isEnabled(FeatureKey.RECIPE_WRITE, platform)) {
            throw new BizException(CODE_RECIPE_WRITE_SUSPENDED, MSG_RECIPE_WRITE_SUSPENDED);
        }
    }
}
