package com.gudu.xsd.modules.vision.application;

import com.gudu.xsd.common.BizException;
import com.gudu.xsd.modules.featureswitch.application.FeatureSwitchAppService;
import com.gudu.xsd.modules.featureswitch.domain.model.FeatureKey;
import com.gudu.xsd.modules.featureswitch.domain.model.Platform;
import org.springframework.stereotype.Component;

/**
 * 拍照识别紧急关停判定（按端）。只拦「创建识别任务」（唯一调大模型的入口）；
 * 查看 / 应用已有结果不拦。
 */
@Component
public class VisionSwitchPolicy {

    public static final int CODE_VISION_SUSPENDED = 5034;
    public static final String MSG_VISION_SUSPENDED = "拍照识别暂停服务";

    private final FeatureSwitchAppService switches;

    public VisionSwitchPolicy(FeatureSwitchAppService switches) {
        this.switches = switches;
    }

    /** 该端已关停则抛 5034。 */
    public void requireEnabled(Platform platform) {
        if (!switches.isEnabled(FeatureKey.VISION, platform)) {
            throw new BizException(CODE_VISION_SUSPENDED, MSG_VISION_SUSPENDED);
        }
    }
}
