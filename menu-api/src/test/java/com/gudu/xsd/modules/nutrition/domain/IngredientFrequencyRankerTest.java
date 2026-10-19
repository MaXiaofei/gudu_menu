package com.gudu.xsd.modules.nutrition.domain;

import com.gudu.xsd.modules.nutrition.domain.service.IngredientFrequencyRanker;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class IngredientFrequencyRankerTest {

    private final IngredientFrequencyRanker ranker = new IngredientFrequencyRanker();

    @Test
    void 做过的菜权重2_菜谱库权重1_同分按id升序_截断() {
        // 1: cooked 2 → 4 ; 2: recipe 5 → 5 ; 3: cooked 1 + recipe 2 → 4 ; 4: recipe 1
        Map<Long, Integer> cooked = Map.of(1L, 2, 3L, 1);
        Map<Long, Integer> recipe = Map.of(2L, 5, 3L, 2, 4L, 1);
        assertThat(ranker.rank(cooked, recipe, 10)).containsExactly(2L, 1L, 3L, 4L);
        assertThat(ranker.rank(cooked, recipe, 2)).containsExactly(2L, 1L);
    }

    @Test
    void 零分剔除_空数据_非法limit() {
        Map<Long, Integer> recipe = new HashMap<>();
        recipe.put(1L, 0);
        recipe.put(2L, null);
        recipe.put(null, 3);
        assertThat(ranker.rank(null, recipe, 5)).isEmpty();
        assertThat(ranker.rank(null, null, 5)).isEmpty();
        assertThat(ranker.rank(Map.of(1L, 1), null, 0)).isEmpty();
    }
}
