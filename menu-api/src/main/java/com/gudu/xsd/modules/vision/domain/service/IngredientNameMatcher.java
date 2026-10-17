package com.gudu.xsd.modules.vision.domain.service;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 识别名 → 食材字典对齐（纯函数）：精确 → 别名表 → 包含（字典名包含于识别名，取最长；
 * 否则识别名包含于字典名，取最短）。别名表与种子脚本 ingredient-alias.json 同源。
 */
public final class IngredientNameMatcher {

    public record Entry(Long id, String name, String iconKey, Long categoryId) {
    }

    public enum How { EXACT, ALIAS, CONTAINS }

    public record Match(Entry entry, How how) {
    }

    private static final int MIN_CONTAINS_LEN = 2;

    private final Map<String, Entry> byName = new HashMap<>();
    private final Map<String, String> alias = new HashMap<>();
    private final List<Entry> entries;

    public IngredientNameMatcher(Collection<Entry> dictionary, Map<String, String> aliasMap) {
        this.entries = dictionary == null ? List.of()
                : dictionary.stream().filter(e -> e != null && !normalize(e.name()).isEmpty()).toList();
        for (Entry e : this.entries) {
            byName.putIfAbsent(normalize(e.name()), e);
        }
        if (aliasMap != null) aliasMap.forEach((k, v) -> alias.put(normalize(k), v));
    }

    public Optional<Match> match(String rawName) {
        String n = normalize(rawName);
        if (n.isEmpty()) return Optional.empty();
        Entry exact = byName.get(n);
        if (exact != null) return Optional.of(new Match(exact, How.EXACT));
        String aliased = alias.get(n);
        if (aliased != null && byName.containsKey(normalize(aliased))) {
            return Optional.of(new Match(byName.get(normalize(aliased)), How.ALIAS));
        }
        Optional<Entry> inside = entries.stream()
                .filter(e -> normalize(e.name()).length() >= MIN_CONTAINS_LEN && n.contains(normalize(e.name())))
                .max(Comparator.comparingInt(e -> normalize(e.name()).length()));
        if (inside.isPresent()) return Optional.of(new Match(inside.get(), How.CONTAINS));
        if (n.length() >= MIN_CONTAINS_LEN) {
            Optional<Entry> outer = entries.stream()
                    .filter(e -> normalize(e.name()).contains(n))
                    .min(Comparator.comparingInt(e -> normalize(e.name()).length()));
            if (outer.isPresent()) return Optional.of(new Match(outer.get(), How.CONTAINS));
        }
        return Optional.empty();
    }

    static String normalize(String s) {
        return s == null ? "" : s.replaceAll("[\\s（）()·]", "").trim();
    }
}
