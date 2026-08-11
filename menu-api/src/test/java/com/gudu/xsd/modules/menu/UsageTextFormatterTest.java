package com.gudu.xsd.modules.menu;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UsageTextFormatter 边界单测（V55 用量原文格式化，P1-3 补）。
 * 覆盖：amount null / dishName null / 单位缺失 / 份数缩放 / 数字去尾零。
 */
class UsageTextFormatterTest {

    @Test
    void amount为null_返回null() {
        assertThat(UsageTextFormatter.format(1L, "番茄炒蛋", null, "个", BigDecimal.ONE)).isNull();
    }

    @Test
    void dishName为null_兜底菜井id() {
        assertThat(UsageTextFormatter.format(7L, null, new BigDecimal("2"), "个", BigDecimal.ONE))
                .isEqualTo("菜#7 2个");
    }

    @Test
    void 单位为null_只显数字() {
        assertThat(UsageTextFormatter.format(1L, "番茄炒蛋", new BigDecimal("100"), null, BigDecimal.ONE))
                .isEqualTo("番茄炒蛋 100");
    }

    @Test
    void 单位为空白_只显数字() {
        assertThat(UsageTextFormatter.format(1L, "番茄炒蛋", new BigDecimal("100"), "  ", BigDecimal.ONE))
                .isEqualTo("番茄炒蛋 100");
    }

    @Test
    void 份数大于1_追加乘N() {
        assertThat(UsageTextFormatter.format(1L, "番茄炒蛋", new BigDecimal("2"), "个", new BigDecimal("3")))
                .isEqualTo("番茄炒蛋 2个 ×3");
    }

    @Test
    void 份数为null_不拼乘N() {
        assertThat(UsageTextFormatter.format(1L, "番茄炒蛋", new BigDecimal("2"), "个", null))
                .isEqualTo("番茄炒蛋 2个");
    }

    @Test
    void 份数等于1_不拼乘N() {
        assertThat(UsageTextFormatter.format(1L, "番茄炒蛋", new BigDecimal("2"), "个", BigDecimal.ONE))
                .isEqualTo("番茄炒蛋 2个");
    }

    @Test
    void 数字去尾零_整数和小数() {
        assertThat(UsageTextFormatter.format(1L, "汤", new BigDecimal("2.00"), "个", BigDecimal.ONE))
                .isEqualTo("汤 2个");
        assertThat(UsageTextFormatter.format(1L, "汤", new BigDecimal("2.50"), "个", BigDecimal.ONE))
                .isEqualTo("汤 2.5个");
        assertThat(UsageTextFormatter.format(1L, "汤", new BigDecimal("0.5"), "kg", new BigDecimal("1.50")))
                .isEqualTo("汤 0.5kg ×1.5");
    }

    @Test
    void 适量_无数字单位_仍按量词展示() {
        // 「适量」场景：amount=1，单位=「适量」量词
        assertThat(UsageTextFormatter.format(1L, "番茄炒蛋", new BigDecimal("1"), "适量", BigDecimal.ONE))
                .isEqualTo("番茄炒蛋 1适量");
    }
}
