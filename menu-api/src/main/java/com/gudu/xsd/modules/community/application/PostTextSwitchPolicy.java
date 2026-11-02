package com.gudu.xsd.modules.community.application;

import com.gudu.xsd.common.BizException;
import com.gudu.xsd.modules.featureswitch.application.FeatureSwitchAppService;
import com.gudu.xsd.modules.featureswitch.domain.model.FeatureKey;
import com.gudu.xsd.modules.featureswitch.domain.model.Platform;
import org.springframework.stereotype.Component;

/** AI 帮写紧急关停判定（按端）。只拦 /community/drafts/ai-text（调大模型写文案）；发帖本身不受影响。 */
@Component
public class PostTextSwitchPolicy {

    public static final int CODE_POST_TEXT_SUSPENDED = 5037;
    public static final String MSG_POST_TEXT_SUSPENDED = "AI 帮写暂停服务";

    private final FeatureSwitchAppService switches;

    public PostTextSwitchPolicy(FeatureSwitchAppService switches) {
        this.switches = switches;
    }

    /** 该端已关停则抛 5037。 */
    public void requireEnabled(Platform platform) {
        if (!switches.isEnabled(FeatureKey.POST_TEXT, platform)) {
            throw new BizException(CODE_POST_TEXT_SUSPENDED, MSG_POST_TEXT_SUSPENDED);
        }
    }
}
