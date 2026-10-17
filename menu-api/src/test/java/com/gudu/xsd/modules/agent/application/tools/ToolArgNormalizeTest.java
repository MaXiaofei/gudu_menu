package com.gudu.xsd.modules.agent.application.tools;

import com.gudu.xsd.modules.agent.domain.service.IngredientSynonyms;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 评测首版基线里约 1/4 的参数失败是约束放错层级（与 query 同级）导致服务端没收到约束。 */
class ToolArgNormalizeTest {

    @Test
    @SuppressWarnings("unchecked")
    void search_recipes_约束放在顶层时挪回filters并合并() {
        Map<String, Object> in = Map.of("query", "汤", "forMembers", List.of("朵朵", "妈妈"),
                "excludeTags", List.of("辣"), "filters", Map.of("excludeTags", List.of("重口")), "limit", 5);
        Map<String, Object> out = new SearchRecipesTool().normalize(in);
        Map<String, Object> f = (Map<String, Object>) out.get("filters");
        assertThat(out).doesNotContainKeys("forMembers", "excludeTags");
        assertThat((List<String>) f.get("forMembers")).containsExactly("朵朵", "妈妈");
        assertThat((List<String>) f.get("excludeTags")).containsExactly("重口", "辣");
        assertThat(out.get("limit")).isEqualTo(5);
    }

    @Test
    void search_recipes_检索词同义归一() {
        Map<String, Object> out = new SearchRecipesTool().normalize(
                Map.of("query", "西蓝花", "ingredients", List.of("洋芋", "土豆", "西红柿")));
        assertThat(out.get("query")).isEqualTo("西兰花");
        assertThat(out.get("ingredients")).isEqualTo(List.of("土豆", "番茄"));
        assertThat(IngredientSynonyms.canonical("麻婆豆付")).isEqualTo("麻婆豆腐");
    }

    @Test
    void search_recipes_参数本来就规整时不变() {
        Map<String, Object> in = Map.of("query", "快手菜", "filters", Map.of("maxMinutes", 10));
        assertThat(new SearchRecipesTool().normalize(in)).isEqualTo(in);
    }

    @Test
    void recommend_tonight_forMembers放进filters时挪回顶层() {
        Map<String, Object> out = new RecommendTonightTool().normalize(
                Map.of("n", 3, "filters", Map.of("forMembers", List.of("林林"))));
        assertThat(out.get("forMembers")).isEqualTo(List.of("林林"));
        Map<String, Object> keep = Map.of("n", 3, "forMembers", List.of("妈妈"));
        assertThat(new RecommendTonightTool().normalize(keep)).isSameAs(keep);
    }
}
