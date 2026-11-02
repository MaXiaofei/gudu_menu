package com.gudu.xsd.modules.ai;

import com.gudu.xsd.common.BizException;
import com.gudu.xsd.modules.dict.CatalogWriteGuard;
import com.gudu.xsd.modules.featureswitch.application.FeatureSwitchAppService;
import com.gudu.xsd.modules.featureswitch.domain.model.FeatureKey;
import com.gudu.xsd.modules.featureswitch.domain.model.Platform;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 营养估算紧急关停：按端拦，业务码 5036；管理后台运营补全食材营养豁免（不带 X-Client 会被归为 App，默认关）。 */
class EstimateSwitchPolicyTest {

    private final FeatureSwitchAppService switches = mock(FeatureSwitchAppService.class);
    private final CatalogWriteGuard guard = mock(CatalogWriteGuard.class);
    private final EstimateSwitchPolicy policy = new EstimateSwitchPolicy(switches, guard);

    @Test
    void 关停时一餐估算与普通用户补全抛5036() {
        when(switches.isEnabled(FeatureKey.ESTIMATE, Platform.MP)).thenReturn(false);
        when(guard.canMaintainPublic()).thenReturn(false);
        assertThatThrownBy(() -> policy.requireEnabled(Platform.MP)).isInstanceOfSatisfying(BizException.class, e -> {
            assertThat(e.getCode()).isEqualTo(5036);
            assertThat(e.getMessage()).isEqualTo("营养估算暂停服务");
        });
        assertThatThrownBy(() -> policy.requireEnabledForFill(Platform.MP))
                .isInstanceOfSatisfying(BizException.class, e -> assertThat(e.getCode()).isEqualTo(5036));
    }

    @Test
    void 开放时放行_只看营养估算开关() {
        when(switches.isEnabled(FeatureKey.ESTIMATE, Platform.MP)).thenReturn(true);
        when(switches.isEnabled(FeatureKey.AGENT_CHAT, Platform.MP)).thenReturn(false);
        assertThatCode(() -> policy.requireEnabled(Platform.MP)).doesNotThrowAnyException();
        assertThatCode(() -> policy.requireEnabledForFill(Platform.MP)).doesNotThrowAnyException();
    }

    @Test
    void 后台运营补全食材营养_App端关停也放行_一餐估算无豁免() {
        when(switches.isEnabled(FeatureKey.ESTIMATE, Platform.APP)).thenReturn(false); // 后台无 X-Client → App
        when(guard.canMaintainPublic()).thenReturn(true);
        assertThatCode(() -> policy.requireEnabledForFill(Platform.APP)).doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.requireEnabled(Platform.APP)).isInstanceOf(BizException.class);
    }

    @Test
    void 非后台运营_不豁免() {
        when(switches.isEnabled(FeatureKey.ESTIMATE, Platform.APP)).thenReturn(false);
        when(guard.canMaintainPublic()).thenReturn(false); // 普通用户 / 运营在小程序里（非 admin scope）
        assertThatThrownBy(() -> policy.requireEnabledForFill(Platform.APP)).isInstanceOf(BizException.class);
    }
}
