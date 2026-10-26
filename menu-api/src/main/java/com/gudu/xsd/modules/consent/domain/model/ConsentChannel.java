package com.gudu.xsd.modules.consent.domain.model;

import java.util.Locale;

/** 同意发生的端：微信小程序 / App / 网页；未知来源记 UNKNOWN（不拒绝，留痕优先）。 */
public enum ConsentChannel {
    MP_WEIXIN, APP, H5, UNKNOWN;

    public static ConsentChannel of(String s) {
        if (s == null || s.isBlank()) return UNKNOWN;
        String v = s.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        try {
            return valueOf(v);
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }
}
