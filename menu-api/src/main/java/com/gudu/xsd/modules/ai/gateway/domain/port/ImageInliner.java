package com.gudu.xsd.modules.ai.gateway.domain.port;

/**
 * 图片转 base64 data URL：视觉请求里供应商下载公网图片失败（需 60s 内可下载）时，由服务端下载后内联重发。
 */
public interface ImageInliner {

    /** @throws IllegalArgumentException 非 http(s)、内网地址、非图片、超过大小上限 */
    String toDataUrl(String imageUrl);
}
