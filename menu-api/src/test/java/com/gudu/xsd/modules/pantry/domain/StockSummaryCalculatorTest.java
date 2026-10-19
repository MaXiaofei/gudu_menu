package com.gudu.xsd.modules.pantry.domain;

import com.gudu.xsd.modules.pantry.domain.model.StockItem;
import com.gudu.xsd.modules.pantry.domain.model.StockLevel;
import com.gudu.xsd.modules.pantry.domain.service.StockSummaryCalculator;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StockSummaryCalculatorTest {

    static final LocalDate TODAY = LocalDate.of(2026, 10, 16);

    @Test
    void 三档计数_3天内到期含已过期不含用完_按品类计数() {
        List<StockItem> items = List.of(
                StockItem.rehydrate(1L, 1L, StockLevel.ENOUGH, TODAY.plusDays(1), null),   // 蔬菜 临期
                StockItem.rehydrate(2L, 2L, StockLevel.LOW, TODAY.minusDays(1), null),     // 蔬菜 已过期
                StockItem.rehydrate(3L, 3L, StockLevel.ENOUGH, TODAY.plusDays(10), null),  // 肉
                StockItem.rehydrate(4L, 4L, StockLevel.NONE, null, null),                  // 未分类 用完
                StockItem.rehydrate(5L, 5L, StockLevel.ENOUGH, null, null));               // 蔬菜
        Map<Long, Long> cat = Map.of(1L, 10L, 2L, 10L, 3L, 20L, 5L, 10L);

        StockSummaryCalculator.Summary s = StockSummaryCalculator.summarize(items, cat::get, TODAY);

        assertThat(s.enough()).isEqualTo(3);
        assertThat(s.low()).isEqualTo(1);
        assertThat(s.out()).isEqualTo(1);
        assertThat(s.expiring3d()).isEqualTo(2);
        assertThat(s.byCategory()).containsEntry(10L, 3).containsEntry(20L, 1).containsEntry(null, 1);
    }

    @Test
    void 空余量() {
        StockSummaryCalculator.Summary s = StockSummaryCalculator.summarize(List.of(), id -> null, TODAY);
        assertThat(s.enough() + s.low() + s.out() + s.expiring3d()).isZero();
        assertThat(s.byCategory()).isEmpty();
    }
}
