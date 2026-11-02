package com.gudu.xsd.modules.mealplan.application;

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

/** 一周餐程紧急关停：按端拦生成草稿，业务码 5035。 */
class MealPlanSwitchPolicyTest {

    @Test
    void App关闭_拦App_小程序放行() {
        FeatureSwitchAppService s = mock(FeatureSwitchAppService.class);
        when(s.isEnabled(FeatureKey.MEAL_PLAN, Platform.APP)).thenReturn(false);
        when(s.isEnabled(FeatureKey.MEAL_PLAN, Platform.MP)).thenReturn(true);
        var p = new MealPlanSwitchPolicy(s);

        assertThatThrownBy(() -> p.requireEnabled(Platform.APP))
                .isInstanceOfSatisfying(BizException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(5035);
                    assertThat(e.getMessage()).isEqualTo("一周餐程暂停服务");
                });
        assertThatCode(() -> p.requireEnabled(Platform.MP)).doesNotThrowAnyException();
    }

    @Test
    void 只看一周餐程开关_不受拍照识别影响() {
        FeatureSwitchAppService s = mock(FeatureSwitchAppService.class);
        when(s.isEnabled(FeatureKey.MEAL_PLAN, Platform.MP)).thenReturn(true);
        when(s.isEnabled(FeatureKey.VISION, Platform.MP)).thenReturn(false);
        assertThatCode(() -> new MealPlanSwitchPolicy(s).requireEnabled(Platform.MP)).doesNotThrowAnyException();
    }
}
