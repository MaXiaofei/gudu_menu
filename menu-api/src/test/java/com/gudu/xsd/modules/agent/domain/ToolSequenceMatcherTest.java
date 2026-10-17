package com.gudu.xsd.modules.agent.domain;

import com.gudu.xsd.modules.agent.domain.service.ToolSequenceMatcher;
import com.gudu.xsd.modules.agent.domain.service.ToolSequenceMatcher.Call;
import com.gudu.xsd.modules.agent.domain.service.ToolSequenceMatcher.Expected;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ToolSequenceMatcherTest {

    private final List<Call> actual = List.of(
            new Call("get_pantry", Map.of("expiringDays", 3)),
            new Call("get_family_profile", Map.of()),
            new Call("search_recipes", Map.of("filters", Map.of("forMembers", List.of("妈妈")))));

    @Test
    void subsequence_允许夹杂其他调用_按顺序() {
        var r = ToolSequenceMatcher.match("subsequence", List.of(
                new Expected("get_pantry", Map.of("expiringDays", Map.of("lte", 3))),
                new Expected("search_recipes", Map.of("filters.forMembers", Map.of("contains", "妈妈")))), List.of(), actual);
        assertThat(r.pass()).isTrue();
        var wrongOrder = ToolSequenceMatcher.match("subsequence", List.of(
                new Expected("search_recipes", null), new Expected("get_pantry", null)), List.of(), actual);
        assertThat(wrongOrder.selectionOk()).isFalse();
    }

    @Test
    void 参数不符_选择正确但参数计为失败() {
        var r = ToolSequenceMatcher.match("subsequence", List.of(
                new Expected("get_pantry", Map.of("expiringDays", Map.of("lte", 1)))), List.of(), actual);
        assertThat(r.selectionOk()).isTrue();
        assertThat(r.argsMatched()).isZero();
        assertThat(r.pass()).isFalse();
    }

    @Test
    void exact_set_none_与_forbid() {
        assertThat(ToolSequenceMatcher.match("exact", List.of(new Expected("get_pantry", null)), List.of(), actual)
                .selectionOk()).as("exact 数量不同").isFalse();
        assertThat(ToolSequenceMatcher.match("set", List.of(new Expected("search_recipes", null),
                new Expected("get_pantry", null)), List.of(), actual).selectionOk()).as("set 不计顺序").isTrue();
        assertThat(ToolSequenceMatcher.match("none", List.of(), List.of(), actual).selectionOk()).isFalse();
        assertThat(ToolSequenceMatcher.match("none", List.of(), List.of(), List.of()).selectionOk()).isTrue();
        var forbidden = ToolSequenceMatcher.match("subsequence", List.of(), List.of("search_recipes"), actual);
        assertThat(forbidden.selectionOk()).isFalse();
        assertThat(forbidden.forbiddenCalled()).containsExactly("search_recipes");
    }

    @Test
    void anyOf_命中任一备选() {
        var alt = new Expected(null, null, List.of(new Expected("recommend_tonight", Map.of()),
                new Expected("search_recipes", Map.of("filters.forMembers", Map.of("contains", "妈妈")))));
        assertThat(ToolSequenceMatcher.match("subsequence", List.of(alt), List.of(), actual).pass()).isTrue();
        var none = new Expected(null, null, List.of(new Expected("recommend_tonight", Map.of())));
        assertThat(ToolSequenceMatcher.match("subsequence", List.of(none), List.of(), actual).selectionOk()).isFalse();
    }
}
