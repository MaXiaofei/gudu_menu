package com.gudu.xsd.modules.agent.application;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;

/** 游标分页：(时间, id) 编码为不透明字符串。 */
public record Cursor(LocalDateTime at, String id) {

    public static String encode(LocalDateTime at, String id) {
        if (at == null || id == null) return null;
        String raw = at.toInstant(ZoneOffset.UTC).toEpochMilli() + "_" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** 非法游标视为从头开始。 */
    public static Cursor decode(String cursor) {
        if (cursor == null || cursor.isBlank()) return new Cursor(null, null);
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int i = raw.indexOf('_');
            return new Cursor(LocalDateTime.ofInstant(Instant.ofEpochMilli(Long.parseLong(raw.substring(0, i))),
                    ZoneOffset.UTC), raw.substring(i + 1));
        } catch (RuntimeException e) {
            return new Cursor(null, null);
        }
    }

    public static int size(Integer size) {
        return size == null ? 20 : Math.max(1, Math.min(size, 50));
    }
}
