package com.gudu.xsd.modules.agent.domain;

import com.gudu.xsd.modules.agent.domain.service.ArgMatcher;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ArgMatcherTest {

    private final Map<String, Object> args = Map.of(
            "menu", "今晚的饭",
            "dishes", List.of("凉拌菠菜", "家常豆腐"),
            "filters", Map.of("tags", List.of("少盐"), "forMembers", List.of("妈妈"), "maxMinutes", 30),
            "items", List.of(Map.of("name", "小葱", "amount", "1"), Map.of("name", "蒜", "unit", "头")),
            "servings", 4);

    @Test
    void 字面值_等于_数组时表示包含() {
        assertThat(ArgMatcher.matches("今晚的饭", "今晚的饭")).isTrue();
        assertThat(ArgMatcher.matches(List.of("a", "b"), "b")).isTrue();
        assertThat(ArgMatcher.matches(4, "4")).as("数字与数字字符串按数值比较").isTrue();
        assertThat(ArgMatcher.matches("今晚", "今晚的饭")).isFalse();
    }

    @Test
    void 各算子() {
        assertThat(ArgMatcher.matches("今晚的饭", Map.of("eq", "今晚的饭"))).isTrue();
        assertThat(ArgMatcher.matches("ONGOING", Map.of("in", List.of("ONGOING", "UPCOMING")))).isTrue();
        assertThat(ArgMatcher.matches(List.of("凉拌菠菜"), Map.of("contains", "凉拌菠菜"))).isTrue();
        assertThat(ArgMatcher.matches("周六 · 爸妈来吃饭", Map.of("contains", "周六"))).as("字符串子串").isTrue();
        assertThat(ArgMatcher.matches(List.of("辣"), Map.of("notContains", "少盐"))).isTrue();
        assertThat(ArgMatcher.matches(List.of("a", "b", "c"), Map.of("containsAll", List.of("a", "c")))).isTrue();
        assertThat(ArgMatcher.matches(List.of("a"), Map.of("containsAll", List.of("a", "c")))).isFalse();
        assertThat(ArgMatcher.matches(List.of("鲈鱼"), Map.of("containsAny", List.of("菠菜", "鲈鱼")))).isTrue();
        assertThat(ArgMatcher.matches(3, Map.of("lte", 3, "gte", 1))).as("同对象多算子为且").isTrue();
        assertThat(ArgMatcher.matches(5, Map.of("lte", 3, "gte", 1))).isFalse();
        assertThat(ArgMatcher.matches("周六 · 爸妈来吃饭", Map.of("regex", "周六|爸妈"))).isTrue();
        assertThat(ArgMatcher.matches("x", Map.of("any", true))).isTrue();
        assertThat(ArgMatcher.matches(null, Map.of("any", true))).isFalse();
    }

    @Test
    void 点号路径与数组投影() {
        assertThat(ArgMatcher.resolve(args, "filters.forMembers")).isEqualTo(List.of("妈妈"));
        assertThat(ArgMatcher.resolve(args, "items[].name")).isEqualTo(List.of("小葱", "蒜"));
        assertThat(ArgMatcher.resolve(args, "items.name")).isEqualTo(List.of("小葱", "蒜"));
        assertThat(ArgMatcher.resolve(args, "items.1.unit")).isEqualTo("头");
        assertThat(ArgMatcher.resolve(args, "nope.x")).isNull();
        assertThat(ArgMatcher.mismatches(args, Map.of(
                "menu", Map.of("eq", "今晚的饭"),
                "dishes", Map.of("containsAll", List.of("凉拌菠菜", "家常豆腐")),
                "filters.tags", Map.of("contains", "少盐"),
                "items[].name", Map.of("contains", "小葱"),
                "servings", Map.of("gte", 3)))).isEmpty();
        assertThat(ArgMatcher.mismatches(args, Map.of("filters.maxMinutes", Map.of("lte", 20))))
                .containsExactly("filters.maxMinutes");
    }
}
