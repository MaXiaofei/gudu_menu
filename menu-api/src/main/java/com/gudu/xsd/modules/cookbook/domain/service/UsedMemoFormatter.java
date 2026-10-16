package com.gudu.xsd.modules.cookbook.domain.service;

import java.util.Collection;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 用材总结 memo（纯函数）："用完:1,2;用了一些:3"（ingredientId，分号分隔两段），全空返回 null。
 * 食记页按此格式解析展示（格式与旧 CookService.buildUsedMemo 保持一致）。
 */
public final class UsedMemoFormatter {

    private UsedMemoFormatter() {
    }

    public static String format(Collection<Long> usedUp, Collection<Long> partiallyUsed) {
        String used = join(usedUp);
        String partial = join(partiallyUsed);
        if (used.isEmpty() && partial.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        if (!used.isEmpty()) sb.append("用完:").append(used);
        if (!partial.isEmpty()) {
            if (sb.length() > 0) sb.append(';');
            sb.append("用了一些:").append(partial);
        }
        return sb.toString();
    }

    private static String join(Collection<Long> ids) {
        return ids == null ? "" : ids.stream().filter(Objects::nonNull)
                .map(String::valueOf).collect(Collectors.joining(","));
    }
}
