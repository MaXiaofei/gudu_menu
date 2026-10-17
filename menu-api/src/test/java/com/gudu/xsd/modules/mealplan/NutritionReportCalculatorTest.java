package com.gudu.xsd.modules.mealplan;

import com.gudu.xsd.modules.mealplan.domain.model.DishCandidate;
import com.gudu.xsd.modules.mealplan.domain.model.DishKind;
import com.gudu.xsd.modules.mealplan.domain.model.MemberHealth;
import com.gudu.xsd.modules.mealplan.domain.model.NutrientKey;
import com.gudu.xsd.modules.mealplan.domain.model.Nutrients;
import com.gudu.xsd.modules.mealplan.domain.service.NutritionReportCalculator;
import com.gudu.xsd.modules.mealplan.domain.service.NutritionTargets;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class NutritionReportCalculatorTest {

    static final LocalDate MON = LocalDate.of(2026, 10, 12);

    @Test
    void 高血压成员_钠超标提醒_来源菜与替换建议_达标项() {
        MemberHealth mama = new MemberHealth(Set.of("高血压"), Set.of(), null, null, null, null);
        Map<NutrientKey, Double> targets = NutritionTargets.of(mama);
        assertThat(targets.get(NutrientKey.SODIUM)).isEqualTo(2000.0);

        // 红烧肉(1) 1500mg、麻婆豆腐(2) 1600mg、炒青菜(3) 800mg + 高纤维
        List<NutritionReportCalculator.Intake> intakes = List.of(
                intake(MON, 1, 1500, 3), intake(MON, 3, 800, 14),
                intake(MON.plusDays(1), 2, 1600, 3), intake(MON.plusDays(1), 3, 800, 14),
                intake(MON.plusDays(3), 1, 1500, 3), intake(MON.plusDays(3), 3, 800, 14));
        List<DishCandidate> catalog = List.of(cand(1, 1500), cand(2, 1600), cand(3, 800), cand(4, 150),
                cand(5, 90), cand(6, 950));
        NutritionReportCalculator.Result r = NutritionReportCalculator.compute(intakes, targets, mama.asProfile(), catalog);

        assertThat(r.recordCount()).isEqualTo(6);
        assertThat(r.activeDays()).isEqualTo(3); // 没食记的周三不摊薄
        NutritionReportCalculator.Alert sodium = r.alerts().stream().filter(a -> a.key() == NutrientKey.SODIUM)
                .findFirst().orElseThrow();
        assertThat(sodium.dailyAvg()).isCloseTo(2333.3, within(0.1));
        assertThat(sodium.limit()).isEqualTo(2000.0);
        assertThat(sodium.sourceDishIds()).containsExactly(1L, 3L);        // 累计贡献最多的两道
        assertThat(sodium.suggestionDishIds()).containsExactly(5L, 4L);    // 符合高血压过滤、最低钠
        assertThat(r.sodiumTrend()).extracting(NutritionReportCalculator.TrendPoint::value)
                .containsExactly(2300.0, 2400.0, 2300.0);
        assertThat(r.goods()).contains("膳食纤维达标", "蛋白质达标").doesNotContain("钠控制在建议量内");
        assertThat(r.radar()).hasSize(5);
        assertThat(r.radar().stream().filter(x -> x.key() == NutrientKey.SODIUM).findFirst().orElseThrow().ratio())
                .isCloseTo(1.17, within(0.01));
    }

    @Test
    void 宝宝阈值更低_无食记时无提醒() {
        MemberHealth baby = new MemberHealth(Set.of("宝宝辅食"), Set.of(), null, null, null, null);
        Map<NutrientKey, Double> t = NutritionTargets.of(baby);
        assertThat(t.get(NutrientKey.SODIUM)).isEqualTo(700.0);
        NutritionReportCalculator.Result r = NutritionReportCalculator.compute(List.of(), t, baby.asProfile(), List.of());
        assertThat(r.alerts()).isEmpty();
        assertThat(r.goods()).isEmpty();
        assertThat(r.activeDays()).isZero();
    }

    private static NutritionReportCalculator.Intake intake(LocalDate d, long dish, double sodium, double fiber) {
        return new NutritionReportCalculator.Intake(d, dish, new Nutrients(400, 20, 15, sodium, fiber));
    }

    private static DishCandidate cand(long id, double sodium) {
        return new DishCandidate(id, "菜" + id, 20, Set.of(), Set.of(), List.of(), DishKind.VEG,
                new Nutrients(300, 10, 8, sodium, 3), 500, false);
    }
}
