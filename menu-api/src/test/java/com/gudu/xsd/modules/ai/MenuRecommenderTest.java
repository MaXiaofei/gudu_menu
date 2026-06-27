package com.gudu.xsd.modules.ai;

import com.gudu.xsd.modules.ai.MenuRecommender.CandidateDish;
import com.gudu.xsd.modules.ai.MenuRecommender.Constraints;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MenuRecommender 纯函数测试（TDD 先红后绿）。
 * 覆盖：健康约束过滤（糖上限）/ 过敏过滤 / 预算上限 / 打分排序 / 组合数。
 *
 * <p>纯函数只做「过滤 + 打分 + 组合」，候选池与营养查表是 IO（AiService 负责），不在此处。
 * 测试 new MenuRecommender()，无外部依赖。
 */
class MenuRecommenderTest {

    private final MenuRecommender r = new MenuRecommender();

    /** 造候选菜：含营养（metricId->per100g 或 per份，本测试直接用 per 份值）+ 价格 + 食材名。 */
    private static CandidateDish dish(long id, String name, BigDecimal price,
                                       double sugar, double protein,
                                       List<String> ingredients) {
        return new CandidateDish(id, name, price,
                Map.of(2L, BigDecimal.valueOf(protein), 5L, BigDecimal.valueOf(sugar)),
                ingredients);
    }

    // ---------------- 健康过滤 ----------------

    @Test
    void 健康过滤_超糖上限剔除() {
        // A 糖 5g（达标），B 糖 30g（超 25 上限）
        var dishes = List.of(
                dish(1, "A", new BigDecimal("10"), 5, 10, List.of()),
                dish(2, "B", new BigDecimal("10"), 30, 10, List.of()));
        var cons = new Constraints(new BigDecimal("25"), null); // sugarMax=25, calMax=null
        var groups = r.recommend(dishes, cons, List.of(), new BigDecimal("1000"), "DAY", 42L);
        // B 被剔除；过滤后任何候选组都不含 dishId=2
        var allDishIds = groups.stream()
                .flatMap(g -> g.dishes().stream())
                .map(d -> d.dishId())
                .toList();
        assertThat(allDishIds).doesNotContain(2L);
        assertThat(allDishIds).contains(1L);
    }

    // ---------------- 过敏过滤 ----------------

    @Test
    void 过敏过滤_含过敏食材剔除() {
        var dishes = List.of(
                dish(1, "花生鸡丁", new BigDecimal("10"), 5, 15, List.of("花生", "鸡肉")),
                dish(2, "番茄炒蛋", new BigDecimal("10"), 5, 10, List.of("番茄", "鸡蛋")));
        var cons = new Constraints(null, null);
        var groups = r.recommend(dishes, cons, List.of("花生"), new BigDecimal("1000"), "DAY", 1L);
        var allDishIds = groups.stream()
                .flatMap(g -> g.dishes().stream()).map(d -> d.dishId()).toList();
        assertThat(allDishIds).doesNotContain(1L); // 含花生被剔
        assertThat(allDishIds).contains(2L);
    }

    // ---------------- 预算 ----------------

    @Test
    void 预算_组合不超budget() {
        var dishes = List.of(
                dish(1, "A", new BigDecimal("30"), 1, 10, List.of()),
                dish(2, "B", new BigDecimal("30"), 1, 10, List.of()),
                dish(3, "C", new BigDecimal("30"), 1, 10, List.of()));
        var cons = new Constraints(null, null);
        BigDecimal budget = new BigDecimal("50");
        var groups = r.recommend(dishes, cons, List.of(), budget, "DAY", 1L);
        // 每组 totalPrice 都不得超过 budget
        for (var g : groups) {
            assertThat(g.totalPrice())
                    .as("候选组总价不得超过预算 50")
                    .isLessThanOrEqualTo(budget);
        }
    }

    // ---------------- 打分排序 ----------------

    @Test
    void 打分顺序_蛋白高的组排前() {
        var dishes = List.of(
                dish(1, "高蛋白", new BigDecimal("10"), 1, 30, List.of()),
                dish(2, "低蛋白", new BigDecimal("10"), 1, 5, List.of()));
        var cons = new Constraints(null, null);
        var groups = r.recommend(dishes, cons, List.of(), new BigDecimal("1000"), "DAY", 1L);
        assertThat(groups).isNotEmpty();
        // 第一组的 score >= 最后一组 score（降序）
        if (groups.size() > 1) {
            assertThat(groups.get(0).score())
                    .isGreaterThanOrEqualTo(groups.get(groups.size() - 1).score());
        }
        // 至少有一组含高蛋白菜
        var topDishIds = groups.get(0).dishes().stream().map(d -> d.dishId()).toList();
        assertThat(topDishIds).contains(1L);
    }

    // ---------------- 组合数（scope） ----------------

    @Test
    void 组合数_DAY至多1组_WEEK至多3组() {
        var dishes = List.of(
                dish(1, "A", new BigDecimal("10"), 1, 10, List.of()),
                dish(2, "B", new BigDecimal("10"), 1, 10, List.of()));
        var cons = new Constraints(null, null);
        var day = r.recommend(dishes, cons, List.of(), new BigDecimal("1000"), "DAY", 1L);
        var week = r.recommend(dishes, cons, List.of(), new BigDecimal("1000"), "WEEK", 1L);
        assertThat(day).hasSizeLessThanOrEqualTo(1);
        assertThat(week).hasSizeLessThanOrEqualTo(3);
    }

    // ---------------- 空候选 ----------------

    @Test
    void 候选全被过滤_返回空列表() {
        var dishes = List.of(dish(1, "X", new BigDecimal("10"), 100, 10, List.of()));
        var cons = new Constraints(new BigDecimal("25"), null); // sugarMax=25 全超
        var groups = r.recommend(dishes, cons, List.of(), new BigDecimal("1000"), "DAY", 1L);
        assertThat(groups).isEmpty();
    }
}
