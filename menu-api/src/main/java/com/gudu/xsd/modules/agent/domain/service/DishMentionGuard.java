package com.gudu.xsd.modules.agent.domain.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 回复菜名一致性（只观测、不拦截）：回复里提到的菜名必须出现在本轮依据里（卡片、待确认动作、工具结果、用户原话）。
 * 识别两类提及：① 菜谱库里的菜名（≥3 字，长名优先，避免「豆腐」误中）；② 书名号 / 引号里的词（可能是库里不存在的菜）。
 */
public final class DishMentionGuard {

    static final int MIN_NAME_LEN = 3;
    private static final Pattern QUOTED = Pattern.compile("[「“\"]([^」”\"]{2,12})[」”\"]");

    private DishMentionGuard() {
    }

    /**
     * @param reply      最终回复
     * @param evidence   本轮依据文本（卡片 JSON、动作标题、工具结果摘要、用户原话等）
     * @param knownNames 菜谱库菜名
     * @return 未在依据中出现的提及（去重，保持出现顺序）
     */
    public static List<String> unsupported(String reply, Collection<String> evidence, Collection<String> knownNames) {
        if (reply == null || reply.isBlank()) return List.of();
        String basis = String.join("\n", evidence == null ? List.of() : evidence);
        Set<String> out = new LinkedHashSet<>();
        List<String> names = new ArrayList<>();
        if (knownNames != null) {
            for (String n : knownNames) if (n != null && n.length() >= MIN_NAME_LEN) names.add(n);
        }
        names.sort((a, b) -> b.length() - a.length());
        String rest = reply;
        for (String n : names) {
            if (!rest.contains(n)) continue;
            if (!basis.contains(n)) out.add(n);
            rest = rest.replace(n, " ");
        }
        Set<String> known = knownNames == null ? Set.of() : Set.copyOf(knownNames);
        Matcher m = QUOTED.matcher(reply);
        while (m.find()) {
            String q = m.group(1).trim();
            if (q.isEmpty() || basis.contains(q)) continue;
            if (known.contains(q) || looksLikeDish(q)) out.add(q);
        }
        return List.copyOf(out);
    }

    /** 引号里的词像菜名：以常见菜式结尾（汤 / 粥 / 鱼 / 肉 / 豆腐 / 炒蛋 …）。 */
    static boolean looksLikeDish(String s) {
        return s.matches(".*(汤|粥|羹|面|饭|鱼|肉|虾|蟹|排骨|鸡翅|豆腐|炒蛋|鸡蛋|青菜|西兰花|丝|片|丁|煲|锅)$");
    }
}
