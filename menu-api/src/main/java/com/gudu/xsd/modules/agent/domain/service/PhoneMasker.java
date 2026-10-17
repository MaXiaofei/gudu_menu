package com.gudu.xsd.modules.agent.domain.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 手机号脱敏（纯函数）：调用链摘要与日志里 138****5678。 */
public final class PhoneMasker {

    private static final Pattern PHONE = Pattern.compile("(?<!\\d)(1[3-9]\\d)(\\d{4})(\\d{4})(?!\\d)");

    private PhoneMasker() {
    }

    public record Masked(String text, int count) {
    }

    public static Masked mask(String text) {
        if (text == null || text.isEmpty()) return new Masked(text, 0);
        Matcher m = PHONE.matcher(text);
        StringBuilder sb = new StringBuilder();
        int n = 0;
        while (m.find()) {
            m.appendReplacement(sb, m.group(1) + "****" + m.group(3));
            n++;
        }
        m.appendTail(sb);
        return new Masked(sb.toString(), n);
    }
}
