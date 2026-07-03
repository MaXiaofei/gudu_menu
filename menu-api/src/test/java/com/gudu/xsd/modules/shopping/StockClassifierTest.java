package com.gudu.xsd.modules.shopping;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * StockClassifier 纯函数单测。参照 ShoppingAggregatorTest 范式。
 * 覆盖：null/0 用量不标记、没有(RED)、不够(YELLOW)、刚好/充足(GREEN)。
 */
class StockClassifierTest {

    private final StockClassifier c = new StockClassifier();

    @Test
    void needGrams为null_不标记() {
        assertThat(c.classify(null, new BigDecimal("100"))).isNull();
    }

    @Test
    void needGrams为零_不标记() {
        assertThat(c.classify(BigDecimal.ZERO, new BigDecimal("100"))).isNull();
    }

    @Test
    void needGrams为负_不标记() {
        assertThat(c.classify(new BigDecimal("-5"), new BigDecimal("100"))).isNull();
    }

    @Test
    void pantry为null_红色没有_差全额() {
        StockClassifier.Result r = c.classify(new BigDecimal("100"), null);
        assertThat(r.status()).isEqualTo(StockClassifier.Status.RED_NONE);
        assertThat(r.shortageGrams()).isEqualByComparingTo("100");
    }

    @Test
    void pantry为零_红色没有_差全额() {
        StockClassifier.Result r = c.classify(new BigDecimal("100"), BigDecimal.ZERO);
        assertThat(r.status()).isEqualTo(StockClassifier.Status.RED_NONE);
        assertThat(r.shortageGrams()).isEqualByComparingTo("100");
    }

    @Test
    void pantry不足_黄色差量() {
        StockClassifier.Result r = c.classify(new BigDecimal("100"), new BigDecimal("30"));
        assertThat(r.status()).isEqualTo(StockClassifier.Status.YELLOW_SHORT);
        assertThat(r.shortageGrams()).isEqualByComparingTo("70");
    }

    @Test
    void pantry刚好_绿色不差() {
        StockClassifier.Result r = c.classify(new BigDecimal("100"), new BigDecimal("100"));
        assertThat(r.status()).isEqualTo(StockClassifier.Status.GREEN_ENOUGH);
        assertThat(r.shortageGrams()).isEqualByComparingTo("0");
    }

    @Test
    void pantry充足_绿色不差() {
        StockClassifier.Result r = c.classify(new BigDecimal("100"), new BigDecimal("150"));
        assertThat(r.status()).isEqualTo(StockClassifier.Status.GREEN_ENOUGH);
        assertThat(r.shortageGrams()).isEqualByComparingTo("0");
    }
}
