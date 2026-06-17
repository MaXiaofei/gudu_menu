package com.yanhuo.xsd.modules.review;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewServiceTest {

    private final ReviewService calc = new ReviewService(null, null);

    record ScoreRow(Long dimensionId, Integer score) implements ReviewService.DimensionScore {}

    @Test
    void 菜品总评均分_按星级求平均() {
        assertThat(calc.averageStar(List.of(5, 4, 3))).isEqualByComparingTo("4.0");
    }

    @Test
    void 空列表返回0() {
        assertThat(calc.averageStar(List.of())).isEqualByComparingTo("0.0");
    }

    @Test
    void 单元素原样返回() {
        assertThat(calc.averageStar(List.of(5))).isEqualByComparingTo("5.0");
    }

    @Test
    void 各维度均分_按维度分组求平均() {
        var scores = List.of(
            new ScoreRow(1L, 4), new ScoreRow(2L, 3),
            new ScoreRow(1L, 5), new ScoreRow(2L, 4));
        Map<Long, String> avg = calc.averageByDimension(scores);
        assertThat(avg.get(1L)).isEqualTo("4.5");
        assertThat(avg.get(2L)).isEqualTo("3.5");
    }
}
