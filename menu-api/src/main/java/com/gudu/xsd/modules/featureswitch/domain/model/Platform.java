package com.gudu.xsd.modules.featureswitch.domain.model;

import com.gudu.xsd.common.web.ClientSource;

import java.util.Arrays;
import java.util.Optional;

/**
 * 开关作用的客户端：微信小程序（mp）/ App（app，含 Flutter 与其他未标明来源的请求）。
 * 端识别沿用请求头 X-Client：{@code mp-weixin} 为小程序，其余（含缺省）按 App。
 */
public enum Platform {

    MP("mp", "小程序"),
    APP("app", "App");

    private final String code;
    private final String label;

    Platform(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String code() {
        return code;
    }

    public String label() {
        return label;
    }

    /** 库里没有开关记录时该端的默认状态：小程序开，App 关（App 暂未上线，防止入口被恶意调用；上线时由运营在后台逐项打开）。 */
    public boolean defaultEnabled() {
        return this == MP;
    }

    /** 由 X-Client 请求头判定端；缺省 / 未知按 App。 */
    public static Platform fromClientHeader(String clientHeader) {
        return ClientSource.isMiniProgram(clientHeader) ? MP : APP;
    }

    public static Optional<Platform> ofCode(String code) {
        return Arrays.stream(values()).filter(p -> p.code.equals(code)).findFirst();
    }
}
