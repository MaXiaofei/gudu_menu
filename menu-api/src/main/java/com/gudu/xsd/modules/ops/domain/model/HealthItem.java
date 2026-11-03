package com.gudu.xsd.modules.ops.domain.model;

import java.util.regex.Pattern;

/**
 * 单项健康探测结果（值对象）。status 取 "ok" / "err"；error 为脱敏后的错误摘要，不含连接串、密码。
 */
public record HealthItem(String key, String label, String status, long latencyMs, String detail, String error) {

    public static final String OK = "ok";
    public static final String ERR = "err";

    private static final int MAX_ERROR_LEN = 80;
    private static final Pattern URL_LIKE = Pattern.compile("(jdbc:)?(jdbc|redis|rediss|postgresql|mysql|https?)://\\S+", Pattern.CASE_INSENSITIVE);
    private static final Pattern SECRET_LIKE = Pattern.compile("(password|passwd|pwd|secret|token|key)\\s*[=:]\\s*\\S+", Pattern.CASE_INSENSITIVE);

    public static HealthItem ok(String key, String label, long latencyMs, String detail) {
        return new HealthItem(key, label, OK, latencyMs, detail, null);
    }

    public static HealthItem fail(String key, String label, long latencyMs, String error) {
        return new HealthItem(key, label, ERR, latencyMs, null, sanitize(error));
    }

    /** 错误摘要脱敏：抹掉连接串与 password=xxx 之类片段，并截断。 */
    public static String sanitize(String raw) {
        if (raw == null || raw.isBlank()) return "未知错误";
        String s = URL_LIKE.matcher(raw).replaceAll("***");
        s = SECRET_LIKE.matcher(s).replaceAll("$1=***");
        s = s.replaceAll("\\s+", " ").trim();
        return s.length() > MAX_ERROR_LEN ? s.substring(0, MAX_ERROR_LEN) + "…" : s;
    }
}
