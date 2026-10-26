package com.gudu.xsd.modules.contentsecurity.domain.port;

/** 内容安全检测服务不可用（不是「内容违规」）：由应用层按用途决定拒绝还是降级。 */
public class ContentSecurityUnavailableException extends RuntimeException {

    public ContentSecurityUnavailableException(String message) {
        super(message);
    }

    public ContentSecurityUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
