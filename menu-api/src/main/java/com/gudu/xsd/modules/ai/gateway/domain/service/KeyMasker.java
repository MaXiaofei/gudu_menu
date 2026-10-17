package com.gudu.xsd.modules.ai.gateway.domain.service;

/** API Key 掩码与日志脱敏（纯函数）：接口与日志永远只出现掩码。 */
public final class KeyMasker {

    private KeyMasker() {
    }

    /** sk-abcdef123456 → sk-****3456；不足 8 位全部掩码；空返回 null。 */
    public static String mask(String key) {
        if (key == null || key.isBlank()) return null;
        String k = key.trim();
        if (k.length() < 8) return "****";
        String prefix = k.startsWith("sk-") ? "sk-" : k.substring(0, 2);
        return prefix + "****" + k.substring(k.length() - 4);
    }

    /** 把错误信息里可能出现的 key 片段替换掉（供应商有时会在 401 文本里回显 key）。 */
    public static String scrub(String text, String key) {
        if (text == null) return null;
        String out = text;
        if (key != null && key.length() >= 8) out = out.replace(key, mask(key));
        out = out.replaceAll("sk-[A-Za-z0-9]{8,}", "sk-****");
        out = out.replaceAll("(?i)(Bearer\\s+)[A-Za-z0-9._\\-]{8,}", "$1****");
        return out.length() > 500 ? out.substring(0, 500) : out;
    }
}
