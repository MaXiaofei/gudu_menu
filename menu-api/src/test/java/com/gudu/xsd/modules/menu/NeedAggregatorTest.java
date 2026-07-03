package com.gudu.xsd.modules.menu;

import com.gudu.xsd.modules.dish.DishIngredient;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 用量聚合纯函数测试。照 PantryServiceTest 范式：new NeedAggregator() 不依赖 Spring。 */
class NeedAggregatorTest {

    private final NeedAggregator agg = new NeedAggregator();

    private MenuDish md(long dishId, String factor) {
        MenuDish m = new MenuDish();
        m.setDishId(dishId);
        m.setServingFactor(new BigDecimal(factor));
        return m;
    }

    private DishIngredient di(long dishId, long ingId, String grams) {
        DishIngredient d = new DishIngredient();
        d.setDishId(dishId);
        d.setIngredientId(ingId);
        d.setGrams(grams == null ? null : new BigDecimal(grams));
        return d;
    }

    @Test
    void 单菜单份_用量等于grams() {
        Map<Long, BigDecimal> need = agg.aggregate(
                List.of(md(1, "1")),
                Map.of(1L, List.of(di(1, 10, "100"))));
        assertThat(need).containsEntry(10L, new BigDecimal("100"));
    }

    @Test
    void 份数翻倍_用量乘份数() {
        Map<Long, BigDecimal> need = agg.aggregate(
                List.of(md(1, "2")),
                Map.of(1L, List.of(di(1, 10, "100"), di(1, 11, "50"))));
        assertThat(need).containsEntry(10L, new BigDecimal("200"))
                        .containsEntry(11L, new BigDecimal("100"));
    }

    @Test
    void 多菜共用食材_用量相加() {
        // 菜1用葱50g、菜2用葱30g，各1份 → 葱共需80g
        Map<Long, BigDecimal> need = agg.aggregate(
                List.of(md(1, "1"), md(2, "1")),
                Map.of(1L, List.of(di(1, 10, "50")),
                       2L, List.of(di(2, 10, "30"))));
        assertThat(need).containsEntry(10L, new BigDecimal("80"));
    }

    @Test
    void 同菜多条menu_dish_份数累加() {
        // audit §6：菜1误加入两次各1份 → 按2份算（扣减只对现有数据正确聚合，去重在加菜接口）
        Map<Long, BigDecimal> need = agg.aggregate(
                List.of(md(1, "1"), md(1, "1")),
                Map.of(1L, List.of(di(1, 10, "100"))));
        assertThat(need).containsEntry(10L, new BigDecimal("200"));
    }

    @Test
    void grams为空_跳过该行() {
        Map<Long, BigDecimal> need = agg.aggregate(
                List.of(md(1, "1")),
                Map.of(1L, List.of(di(1, 10, "100"), di(1, 11, null))));
        assertThat(need).containsOnlyKeys(10L);
    }

    @Test
    void 空输入_返回空map() {
        assertThat(agg.aggregate(List.of(), Map.of())).isEmpty();
        assertThat(agg.aggregate(null, null)).isEmpty();
    }
}
