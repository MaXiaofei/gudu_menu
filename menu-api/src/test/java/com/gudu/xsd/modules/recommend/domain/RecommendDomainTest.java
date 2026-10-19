package com.gudu.xsd.modules.recommend.domain;

import com.gudu.xsd.modules.recommend.domain.model.DishScore;
import com.gudu.xsd.modules.recommend.domain.service.RecommendRanker;
import com.gudu.xsd.modules.recommend.domain.service.SeasonalIngredientPolicy;
import org.junit.jupiter.api.Test;

import java.time.Month;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendDomainTest {

    private final RecommendRanker ranker = new RecommendRanker();

    @Test
    void 覆盖率降序_缺得少_做过多_id升序() {
        List<DishScore> s = List.of(
                new DishScore(1L, 0.5, 1, 0),
                new DishScore(2L, 1.0, 0, 0),
                new DishScore(3L, 0.5, 1, 5),
                new DishScore(4L, 0.5, 2, 9),
                new DishScore(5L, 0.5, 1, 0));
        assertThat(ranker.topByCoverage(s, Set.of(), 10)).containsExactly(2L, 3L, 1L, 5L, 4L);
        assertThat(ranker.topByCoverage(s, Set.of(2L), 2)).containsExactly(3L, 1L);
    }

    @Test
    void 边界_空候选_null排除_非法limit_空id() {
        assertThat(ranker.topByCoverage(null, null, 3)).isEmpty();
        assertThat(ranker.topByCoverage(List.of(new DishScore(1L, 1, 0, 0)), null, 0)).isEmpty();
        assertThat(ranker.topByCoverage(Arrays.asList(new DishScore(null, 1, 0, 0), new DishScore(2L, 0, 0, 0)),
                null, 3)).containsExactly(2L);
    }

    @Test
    void 季节鲜_每月都有_null月份为空() {
        SeasonalIngredientPolicy p = new SeasonalIngredientPolicy();
        for (Month m : Month.values()) assertThat(p.ingredientNamesFor(m)).isNotEmpty();
        assertThat(p.ingredientNamesFor(Month.OCTOBER)).contains("南瓜");
        assertThat(p.ingredientNamesFor(Month.JULY)).contains("番茄");
        assertThat(p.ingredientNamesFor(null)).isEmpty();
    }
}
