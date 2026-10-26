package com.gudu.xsd.common.web;

/**
 * 请求来源端（mini-release-compliance A.1）：客户端在请求头 {@code X-Client} 标明自己，
 * 后端据此对个别端关闭功能（如微信小程序不开放厨房圈写操作）。
 *
 * <p>只用于「按端收窄功能」，不是安全边界——头可被伪造，伪造的结果只是自己少了功能。
 */
public final class ClientSource {

    /** 来源端请求头。 */
    public static final String HEADER = "X-Client";
    /** 微信小程序（menu-mini 的 mp-weixin 构建在 request.ts / upload.ts 统一加）。 */
    public static final String MP_WEIXIN = "mp-weixin";

    private ClientSource() {
    }

    /** 请求头值是否为微信小程序（忽略大小写与首尾空白）。 */
    public static boolean isMiniProgram(String headerValue) {
        return headerValue != null && MP_WEIXIN.equalsIgnoreCase(headerValue.trim());
    }
}
