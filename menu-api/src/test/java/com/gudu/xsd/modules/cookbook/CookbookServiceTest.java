package com.gudu.xsd.modules.cookbook;

import com.gudu.xsd.modules.cookbook.CookbookService.DishMatch;
import com.gudu.xsd.modules.dish.Dish;
import com.gudu.xsd.modules.dish.DishIngredient;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 反向找菜纯逻辑测试：rankDishes 按「全匹配优先、部分匹配次之、缺得越多越靠后」排序。
 * 同 PantryServiceTest 范式：测试 new CookbookService(null,null,null,null)，纯函数不碰 Mapper。
 */
class CookbookServiceTest {

    // 4 个依赖：favorite / cookingRecord / dish / dishIngredient。纯函数 rankDishes 不碰它们。
    private final CookbookService svc = new CookbookService(null, null, null, null);

    private DishIngredient di(Long dishId, Long ingId) {
        DishIngredient d = new DishIngredient();
        d.setDishId(dishId);
        d.setIngredientId(ingId);
        d.setAmount(BigDecimal.ONE);
        return d;
    }

    /** 拼装 dish_ingredient 列表：一道菜用多个食材。 */
    private List<DishIngredient> rels(Long dishId, Long... ingIds) {
        List<DishIngredient> out = new ArrayList<>();
        for (Long id : ingIds) {
            out.add(di(dishId, id));
        }
        return out;
    }

    private Dish dish(Long id, String name) {
        Dish d = new Dish();
        d.setId(id);
        d.setName(name);
        return d;
    }

    /** 全匹配（菜的食材 ⊆ 所选）→ canMake=true，排第一。 */
    @Test
    void 全匹配_可做_排第一() {
        // 菜1：鸡蛋+番茄；菜2：鸡蛋+番茄+盐
        var all = new ArrayList<DishIngredient>();
        all.addAll(rels(1L, 10L, 11L));
        all.addAll(rels(2L, 10L, 11L, 12L));
        var dishes = Map.of(1L, dish(1L, "番茄炒蛋"), 2L, dish(2L, "番茄炒蛋加盐"));
        var ingName = Map.of(10L, "鸡蛋", 11L, "番茄", 12L, "盐", 13L, "葱");

        List<DishMatch> r = svc.rankDishes(all, dishes, ingName, Set.of(10L, 11L, 12L));

        assertThat(r).isNotEmpty();
        assertThat(r.get(0).dish().getId()).isEqualTo(1L);
        assertThat(r.get(0).canMake()).isTrue();
        assertThat(r.get(0).matchCount()).isEqualTo(2);
        assertThat(r.get(0).totalCount()).isEqualTo(2);
        assertThat(r.get(0).missingIngredients()).isEmpty();
    }

    /** 部分匹配（缺 1-2 个）→ canMake=false，排全匹配之后，缺得越少越靠前。 */
    @Test
    void 部分匹配_标注缺失_缺得越少越靠前() {
        var all = new ArrayList<DishIngredient>();
        all.addAll(rels(1L, 10L, 11L));                 // 全匹配
        all.addAll(rels(2L, 10L, 11L, 13L));            // 缺 1（葱）
        all.addAll(rels(3L, 10L, 11L, 13L, 14L));       // 缺 2（葱+蒜）
        var dishes = Map.of(
                1L, dish(1L, "番茄炒蛋"),
                2L, dish(2L, "番茄炒蛋加葱"),
                3L, dish(3L, "番茄炒蛋葱蒜"));
        var ingName = Map.of(10L, "鸡蛋", 11L, "番茄", 13L, "葱", 14L, "蒜");

        List<DishMatch> r = svc.rankDishes(all, dishes, ingName, Set.of(10L, 11L));

        assertThat(r).hasSize(3);
        assertThat(r.get(0).dish().getId()).isEqualTo(1L);              // 全匹配优先
        assertThat(r.get(0).canMake()).isTrue();
        assertThat(r.get(1).dish().getId()).isEqualTo(2L);              // 缺1 排缺2 前
        assertThat(r.get(1).canMake()).isFalse();
        assertThat(r.get(1).missingIngredients()).containsExactly("葱");
        assertThat(r.get(2).dish().getId()).isEqualTo(3L);              // 缺2 最后
        assertThat(r.get(2).missingIngredients()).containsExactly("葱", "蒜");
    }

    /** 缺超过 2 个 → 视为「差太多」，不返回（避免噪声）。 */
    @Test
    void 缺超过2个_不返回() {
        var all = rels(1L, 10L, 11L, 12L, 13L, 14L);  // 缺3
        var dishes = Map.of(1L, dish(1L, "杂烩"));
        var ingName = Map.of(10L, "a", 11L, "b", 12L, "c", 13L, "d", 14L, "e");

        List<DishMatch> r = svc.rankDishes(all, dishes, ingName, Set.of(10L, 11L));

        assertThat(r).isEmpty();
    }

    /** 无任何食材交集的菜 → 不返回。 */
    @Test
    void 无交集_不返回() {
        var all = rels(1L, 20L, 21L);   // 完全不沾边
        var dishes = Map.of(1L, dish(1L, "可乐鸡翅"));
        var ingName = Map.of(20L, "可乐", 21L, "鸡翅");

        List<DishMatch> r = svc.rankDishes(all, dishes, ingName, Set.of(10L, 11L));

        assertThat(r).isEmpty();
    }

    /** 排序稳定性：全匹配组内部，食材总数少者优先（更省库存）。 */
    @Test
    void 全匹配组_食材总数少者优先_利于清库存() {
        var all = new ArrayList<DishIngredient>();
        all.addAll(rels(1L, 10L, 11L, 12L));  // 全匹配，3种
        all.addAll(rels(2L, 10L));            // 全匹配，1种
        var dishes = Map.of(1L, dish(1L, "三拼"), 2L, dish(2L, "单炒蛋"));
        var ingName = Map.of(10L, "鸡蛋", 11L, "番茄", 12L, "盐");

        List<DishMatch> r = svc.rankDishes(all, dishes, ingName, Set.of(10L, 11L, 12L));

        assertThat(r).hasSize(2);
        assertThat(r.get(0).dish().getId()).isEqualTo(2L);  // 单炒蛋（用掉少）排前
        assertThat(r).allMatch(DishMatch::canMake);
    }
}
