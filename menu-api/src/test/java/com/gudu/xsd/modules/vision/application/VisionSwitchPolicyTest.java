package com.gudu.xsd.modules.vision.application;

import com.gudu.xsd.common.BizException;
import com.gudu.xsd.modules.featureswitch.application.FeatureSwitchAppService;
import com.gudu.xsd.modules.featureswitch.domain.model.FeatureKey;
import com.gudu.xsd.modules.featureswitch.domain.model.Platform;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 拍照识别紧急关停：按端拦创建任务，业务码 5034。 */
class VisionSwitchPolicyTest {

    @Test
    void 小程序关闭_拦小程序_App放行() {
        FeatureSwitchAppService s = mock(FeatureSwitchAppService.class);
        when(s.isEnabled(FeatureKey.VISION, Platform.MP)).thenReturn(false);
        when(s.isEnabled(FeatureKey.VISION, Platform.APP)).thenReturn(true);
        var p = new VisionSwitchPolicy(s);

        assertThatThrownBy(() -> p.requireEnabled(Platform.MP))
                .isInstanceOfSatisfying(BizException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(5034);
                    assertThat(e.getMessage()).isEqualTo("拍照识别暂停服务");
                });
        assertThatCode(() -> p.requireEnabled(Platform.APP)).doesNotThrowAnyException();
    }

    @Test
    void 只看拍照识别开关_不受别的类别影响() {
        FeatureSwitchAppService s = mock(FeatureSwitchAppService.class);
        when(s.isEnabled(FeatureKey.VISION, Platform.MP)).thenReturn(true);
        when(s.isEnabled(FeatureKey.MEAL_PLAN, Platform.MP)).thenReturn(false);
        assertThatCode(() -> new VisionSwitchPolicy(s).requireEnabled(Platform.MP)).doesNotThrowAnyException();
    }
}
