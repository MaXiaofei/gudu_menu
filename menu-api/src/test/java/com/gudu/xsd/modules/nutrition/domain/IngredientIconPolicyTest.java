package com.gudu.xsd.modules.nutrition.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gudu.xsd.modules.nutrition.domain.service.IngredientIconPolicy;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 食材插画规则：语义 + 与单一来源 docs/design/ingredient-icons.json、V68 回填 SQL、素材清单逐项一致。
 */
class IngredientIconPolicyTest {

    static final Path ROOT = Path.of("..");
    static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void 最长关键词优先() {
        assertThat(IngredientIconPolicy.byName("鸡蛋")).isEqualTo("egg");      // 鸡蛋(egg,2) > 鸡(wing,1)
        assertThat(IngredientIconPolicy.byName("鸡精")).isEqualTo("sauce");    // 鸡精(sauce,2) > 鸡(wing,1)
        assertThat(IngredientIconPolicy.byName("胡萝卜")).isEqualTo("carrot");
        assertThat(IngredientIconPolicy.byName("蒜苗")).isEqualTo("scallion"); // 蒜苗(2) > 蒜(1)
        assertThat(IngredientIconPolicy.byName("奶油")).isEqualTo("milk");     // 奶油(2) > 油(1)
        assertThat(IngredientIconPolicy.byName("  鲈鱼 ")).isEqualTo("fish");
    }

    @Test
    void 等长取靠前规则() {
        // 葱(scallion) 与 油(oil) 等长，scallion 在前
        assertThat(IngredientIconPolicy.byName("葱油")).isEqualTo("scallion");
    }

    @Test
    void 未命中按品类兜底_都不中为null() {
        assertThat(IngredientIconPolicy.resolve("秋葵", "蔬菜")).isEqualTo("cabbage");
        assertThat(IngredientIconPolicy.resolve("秋葵", "不存在的品类")).isNull();
        assertThat(IngredientIconPolicy.resolve("秋葵", null)).isNull();
        assertThat(IngredientIconPolicy.resolve(null, null)).isNull();
        assertThat(IngredientIconPolicy.resolve("番茄", "水产海鲜")).isEqualTo("tomato"); // 名称优先于品类
    }

    @Test
    void 规则与设计稿JSON逐项一致() throws Exception {
        JsonNode doc = JSON.readTree(ROOT.resolve("docs/design/ingredient-icons.json").toFile());
        List<IngredientIconPolicy.Rule> expected = new ArrayList<>();
        for (JsonNode rule : doc.get("keywords")) {
            List<String> words = new ArrayList<>();
            rule.get(1).forEach(w -> words.add(w.asText()));
            expected.add(new IngredientIconPolicy.Rule(rule.get(0).asText(), words));
        }
        assertThat(IngredientIconPolicy.RULES).containsExactlyElementsOf(expected);

        Map<String, String> cat = new LinkedHashMap<>();
        doc.get("categoryFallback").fields().forEachRemaining(e -> cat.put(e.getKey(), e.getValue().asText()));
        assertThat(IngredientIconPolicy.CATEGORY_FALLBACK).containsExactlyEntriesOf(cat);
    }

    @Test
    void 所有key都有素材() throws Exception {
        JsonNode assets = JSON.readTree(ROOT.resolve("docs/design/assets.json").toFile());
        Set<String> ing = new HashSet<>();
        assets.get("ing").forEach(n -> ing.add(n.asText()));
        IngredientIconPolicy.RULES.forEach(r -> assertThat(ing).contains(r.key()));
        assertThat(ing).containsAll(IngredientIconPolicy.CATEGORY_FALLBACK.values());
    }

    @Test
    void V68回填SQL顺序与规则语义一致() throws Exception {
        String sql = Files.readString(ROOT.resolve("menu-api/sql/V68__ingredient_icon_key.sql"), StandardCharsets.UTF_8);
        Matcher m = Pattern.compile("WHEN name LIKE '%([^%']+)%' THEN '([a-z]+)'").matcher(sql);
        List<String> actual = new ArrayList<>();
        while (m.find()) actual.add(m.group(1) + "=" + m.group(2));

        record Pair(String word, String key, int rule, int idx) { }
        List<Pair> pairs = new ArrayList<>();
        for (int i = 0; i < IngredientIconPolicy.RULES.size(); i++) {
            IngredientIconPolicy.Rule r = IngredientIconPolicy.RULES.get(i);
            for (int j = 0; j < r.keywords().size(); j++) pairs.add(new Pair(r.keywords().get(j), r.key(), i, j));
        }
        pairs.sort(Comparator.comparingInt((Pair p) -> -p.word().length())
                .thenComparingInt(Pair::rule).thenComparingInt(Pair::idx));
        assertThat(actual).containsExactlyElementsOf(pairs.stream().map(p -> p.word() + "=" + p.key()).toList());

        IngredientIconPolicy.CATEGORY_FALLBACK.forEach((name, key) ->
                assertThat(sql).contains("WHEN '" + name + "' THEN '" + key + "'"));
    }
}
