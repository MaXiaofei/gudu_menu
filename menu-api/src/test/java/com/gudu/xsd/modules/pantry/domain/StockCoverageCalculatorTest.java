package com.gudu.xsd.modules.pantry.domain;

import com.gudu.xsd.common.domain.DomainException;
import com.gudu.xsd.modules.pantry.domain.model.Coverage;
import com.gudu.xsd.modules.pantry.domain.model.DishMaterial;
import com.gudu.xsd.modules.pantry.domain.model.MissingIngredient;
import com.gudu.xsd.modules.pantry.domain.model.StockLevel;
import com.gudu.xsd.modules.pantry.domain.service.ShelfLifePolicy;
import com.gudu.xsd.modules.pantry.domain.service.StockCoverageCalculator;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 覆盖率领域服务 + Coverage 值对象 + 保质期建议策略（纯 JUnit）。 */
class StockCoverageCalculatorTest {

    private final StockCoverageCalculator calc = new StockCoverageCalculator();

    @Test
    void 规格场景_4样用料3样够或低_缺1样() {
        List<DishMaterial> m = List.of(
                new DishMaterial(1L, "番茄", false), new DishMaterial(2L, "鸡蛋", false),
                new DishMaterial(3L, "葱", false), new DishMaterial(4L, "牛肉", false));
        Coverage c = calc.calculate(m, Map.of(1L, StockLevel.ENOUGH, 2L, StockLevel.LOW, 3L, StockLevel.ENOUGH,
                4L, StockLevel.NONE));
        assertThat(c.enough()).isEqualTo(3);
        assertThat(c.total()).isEqualTo(4);
        assertThat(c.missing()).containsExactly(new MissingIngredient(4L, "牛肉"));
        assertThat(c.ratio()).isEqualTo(0.75);
        assertThat(c.isComplete()).isFalse();
    }

    @Test
    void 调料不计_重复食材只计一次_未建档算缺_空项跳过() {
        List<DishMaterial> m = Arrays.asList(
                new DishMaterial(1L, "番茄", false), new DishMaterial(1L, "番茄", false),
                new DishMaterial(9L, "盐", true), new DishMaterial(5L, "香菜", false),
                null, new DishMaterial(null, "无id", false));
        Coverage c = calc.calculate(m, Map.of(1L, StockLevel.ENOUGH));
        assertThat(c.total()).isEqualTo(2);
        assertThat(c.enough()).isEqualTo(1);
        assertThat(c.missing()).extracting(MissingIngredient::name).containsExactly("香菜");
    }

    @Test
    void 全够_complete_空用料或null档位表() {
        Coverage full = calc.calculate(List.of(new DishMaterial(1L, "a", false)), Map.of(1L, StockLevel.LOW));
        assertThat(full.isComplete()).isTrue();
        assertThat(calc.calculate(List.of(), Map.of())).isEqualTo(Coverage.empty());
        assertThat(calc.calculate(null, null)).isEqualTo(Coverage.empty());
        Coverage noLevels = calc.calculate(List.of(new DishMaterial(1L, "a", false)), null);
        assertThat(noLevels.enough()).isZero();
        assertThat(Coverage.empty().ratio()).isZero();
        assertThat(Coverage.empty().isComplete()).isFalse();
    }

    @Test
    void Coverage不变量_与不可变() {
        assertThatThrownBy(() -> new Coverage(3, 2, List.of())).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new Coverage(-1, 2, List.of())).isInstanceOf(DomainException.class);
        List<MissingIngredient> src = new ArrayList<>(List.of(new MissingIngredient(1L, "x")));
        Coverage c = new Coverage(0, 1, src);
        src.clear();
        assertThat(c.missing()).hasSize(1);
        assertThat(new Coverage(0, 0, null).missing()).isEmpty();
    }

    @Test
    void 家里够的优先排序_比例降序_其次缺得少() {
        Coverage a = new Coverage(1, 2, List.of(new MissingIngredient(1L, "x")));
        Coverage b = new Coverage(2, 2, List.of());
        Coverage c = new Coverage(2, 4, List.of(new MissingIngredient(1L, "x"), new MissingIngredient(2L, "y")));
        List<Coverage> list = new ArrayList<>(List.of(a, c, b));
        list.sort(StockCoverageCalculator.bestFirst());
        assertThat(list).containsExactly(b, a, c);
    }

    // ---------- ShelfLifePolicy ----------

    private final ShelfLifePolicy policy = new ShelfLifePolicy();

    @Test
    void 保质期建议_字典优先_品类兜底_未知为空() {
        assertThat(policy.suggestedDays(10, "蔬菜")).contains(10);
        assertThat(policy.suggestedDays(null, "蔬菜")).contains(5);
        assertThat(policy.suggestedDays(0, " 乳制品 ")).contains(7);
        assertThat(policy.suggestedDays(null, "外星食材")).isEmpty();
        assertThat(policy.suggestedDays(null, null)).isEmpty();
        assertThat(policy.suggestBestBefore(LocalDate.of(2026, 10, 16), 5)).isEqualTo(LocalDate.of(2026, 10, 21));
    }
}
