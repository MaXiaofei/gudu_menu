package com.gudu.xsd.modules.menu;

import com.gudu.xsd.modules.dish.DishIngredient;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 用量聚合纯函数测试。照 PantryServiceTest 范式：new NeedAggregator() 不依赖 Spring。
 *  V55（食材去单位）：聚合输出用量原文（amount + unitName），不再按克合计。 */
class NeedAggregatorTest {

    private final NeedAggregator agg = new NeedAggregator();

    private MenuDish md(long dishId, String factor) {
        MenuDish m = new MenuDish();
        m.setDishId(dishId);
        m.setServingFactor(new BigDecimal(factor));
        return m;
    }

    private DishIngredient di(long dishId, long ingId, String amount, String unitName) {
        DishIngredient d = new DishIngredient();
        d.setDishId(dishId);
        d.setIngredientId(ingId);
        d.setAmount(amount == null ? null : new BigDecimal(amount));
        d.setUnitName(unitName);
        return d;
    }

    @Test
    void 单菜单份_用量原文保留() {
        Map<Long, List<NeedAggregator.UsageText>> need = agg.aggregate(
                List.of(md(1, "1")),
                Map.of(1L, List.of(di(1, 10, "100", "g"))));
        assertThat(need).containsKey(10L);
        assertThat(need.get(10L)).hasSize(1);
        assertThat(need.get(10L).get(0).amount()).isEqualByComparingTo("100");
        assertThat(need.get(10L).get(0).unitName()).isEqualTo("g");
        assertThat(need.get(10L).get(0).dishId()).isEqualTo(1L);
    }

    @Test
    void 份数翻倍_servingFactor随明细携带() {
        Map<Long, List<NeedAggregator.UsageText>> need = agg.aggregate(
                List.of(md(1, "2")),
                Map.of(1L, List.of(di(1, 10, "100", "g"), di(1, 11, "50", "g"))));
        assertThat(need.get(10L)).hasSize(1);
        assertThat(need.get(10L).get(0).servingFactor()).isEqualByComparingTo("2");
        assertThat(need).containsKey(11L);
    }

    @Test
    void 多菜共用食材_两笔用量原文都保留() {
        // 菜1用番茄100g、菜2用番茄2个 → 番茄两笔明细（不按克合并）
        Map<Long, List<NeedAggregator.UsageText>> need = agg.aggregate(
                List.of(md(1, "1"), md(2, "1")),
                Map.of(1L, List.of(di(1, 10, "100", "g")),
                       2L, List.of(di(2, 10, "2", "个"))));
        assertThat(need.get(10L)).hasSize(2);
        assertThat(need.get(10L)).extracting(NeedAggregator.UsageText::unitName)
                .containsExactlyInAnyOrder("g", "个");
    }

    @Test
    void 同菜多条menu_dish_份数累加() {
        // audit §6：菜1误加入两次各1份 → 按2份算
        Map<Long, List<NeedAggregator.UsageText>> need = agg.aggregate(
                List.of(md(1, "1"), md(1, "1")),
                Map.of(1L, List.of(di(1, 10, "100", "g"))));
        assertThat(need.get(10L).get(0).servingFactor()).isEqualByComparingTo("2");
    }

    @Test
    void 用量为空_跳过该行() {
        Map<Long, List<NeedAggregator.UsageText>> need = agg.aggregate(
                List.of(md(1, "1")),
                Map.of(1L, List.of(di(1, 10, "100", "g"), di(1, 11, null, null))));
        assertThat(need).containsOnlyKeys(10L);
    }

    @Test
    void 空输入_返回空map() {
        assertThat(agg.aggregate(List.of(), Map.of())).isEmpty();
        assertThat(agg.aggregate(null, null)).isEmpty();
    }
}
