package com.gudu.xsd.modules.privacy.interfaces;

import jakarta.servlet.http.HttpServletRequest;

/** 同意记录的来源渠道与客户端 IP（nginx 反代：X-Forwarded-For 首个 / X-Real-IP / remoteAddr）。 */
final class ClientInfo {

    private ClientInfo() {
    }

    static String channel(String fromBody, HttpServletRequest req) {
        String c = fromBody != null && !fromBody.isBlank() ? fromBody : req.getHeader("X-Client");
        return c == null || c.isBlank() ? "unknown" : c.trim();
    }

    static String ip(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        String real = req.getHeader("X-Real-IP");
        if (real != null && !real.isBlank()) return real.trim();
        return req.getRemoteAddr();
    }
}
