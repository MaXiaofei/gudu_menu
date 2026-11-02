package com.gudu.xsd.modules.ai;

import com.gudu.xsd.common.BizException;
import com.gudu.xsd.modules.dict.CatalogWriteGuard;
import com.gudu.xsd.modules.featureswitch.application.FeatureSwitchAppService;
import com.gudu.xsd.modules.featureswitch.domain.model.FeatureKey;
import com.gudu.xsd.modules.featureswitch.domain.model.Platform;
import org.springframework.stereotype.Component;

/**
 * 营养估算紧急关停判定（按端）：拦 /ai/dish/estimate（一餐估算）与 /ai/nutrition/fill（食材营养补全）。
 *
 * <p>运营豁免：管理后台（scope=admin 登录）的平台运营维护公共食材时调 nutrition/fill 不受限——
 * 紧急关停针对终端用户生成内容；后台请求不带 X-Client，会被归为 App 端（默认关），不豁免则运营无法补全营养。
 * 判定沿用 {@link CatalogWriteGuard#canMaintainPublic()}（与「能否维护公共食材」同一口径：admin scope + OPERATOR 及以上），
 * 运营在小程序里（非 admin scope）调用按普通用户处理。
 */
@Component
public class EstimateSwitchPolicy {

    public static final int CODE_ESTIMATE_SUSPENDED = 5036;
    public static final String MSG_ESTIMATE_SUSPENDED = "营养估算暂停服务";

    private final FeatureSwitchAppService switches;
    private final CatalogWriteGuard catalogGuard;

    public EstimateSwitchPolicy(FeatureSwitchAppService switches, CatalogWriteGuard catalogGuard) {
        this.switches = switches;
        this.catalogGuard = catalogGuard;
    }

    /** 一餐估算：该端已关停则抛 5036（无豁免）。 */
    public void requireEnabled(Platform platform) {
        if (!switches.isEnabled(FeatureKey.ESTIMATE, platform)) {
            throw new BizException(CODE_ESTIMATE_SUSPENDED, MSG_ESTIMATE_SUSPENDED);
        }
    }

    /** 食材营养补全：管理后台的平台运营放行，其余按端开关；已关停则抛 5036。 */
    public void requireEnabledForFill(Platform platform) {
        if (catalogGuard.canMaintainPublic()) return;
        requireEnabled(platform);
    }
}
