package com.gudu.xsd.modules.agent.domain.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 回复转纯文本：客户端按纯文本显示，模型偶尔仍输出 Markdown。
 * 去掉粗体 / 斜体 / 行内代码 / 标题符号，列表项（- * • 1.）统一成「· 」开头，压掉多余空行。
 */
public final class PlainReply {

    private static final Pattern BULLET = Pattern.compile("^\\s*(?:[-*•]|\\d{1,2}[.、)])\\s+");
    private static final Pattern HEADING = Pattern.compile("^\\s*#{1,6}\\s*");

    private PlainReply() {
    }

    public static String of(String text) {
        if (text == null || text.isEmpty()) return text;
        String s = text.replace("**", "").replace("__", "").replace("`", "");
        List<String> out = new ArrayList<>();
        boolean blank = false;
        for (String line : s.split("\\R", -1)) {
            String l = HEADING.matcher(line).replaceFirst("");
            if (BULLET.matcher(l).find()) l = "· " + BULLET.matcher(l).replaceFirst("");
            if (l.isBlank()) {
                if (blank || out.isEmpty()) continue;
                blank = true;
            } else {
                blank = false;
            }
            out.add(l.stripTrailing());
        }
        while (!out.isEmpty() && out.get(out.size() - 1).isBlank()) out.remove(out.size() - 1);
        return String.join("\n", out);
    }
}
