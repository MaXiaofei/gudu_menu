package com.gudu.xsd.modules.nutrition.infrastructure;

import com.gudu.xsd.modules.nutrition.domain.repository.IngredientReferences.MergeStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** 食材引用检查与合并（H2 MySQL 模式）：覆盖在用检查、改指向、以及各唯一键冲突的合并规则。 */
class JdbcIngredientReferencesTest {

    private JdbcTemplate jdbc;
    private JdbcIngredientReferences refs;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource ds = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(ds);
        refs = new JdbcIngredientReferences(ds);
        jdbc.execute("CREATE TABLE dish_ingredient (id BIGINT AUTO_INCREMENT PRIMARY KEY, dish_id BIGINT, ingredient_id BIGINT, amount INT, UNIQUE (dish_id, ingredient_id))");
        jdbc.execute("CREATE TABLE ingredient_stock (id BIGINT AUTO_INCREMENT PRIMARY KEY, family_id BIGINT, ingredient_id BIGINT, level VARCHAR(16), best_before DATE, UNIQUE (family_id, ingredient_id))");
        jdbc.execute("CREATE TABLE shopping_item (id BIGINT AUTO_INCREMENT PRIMARY KEY, list_id BIGINT, ingredient_id BIGINT, unit_id BIGINT, UNIQUE (list_id, ingredient_id, unit_id))");
        jdbc.execute("CREATE TABLE menu_prep_status (id BIGINT AUTO_INCREMENT PRIMARY KEY, menu_id BIGINT, ingredient_id BIGINT, UNIQUE (menu_id, ingredient_id))");
        jdbc.execute("CREATE TABLE ingredient_nutrition (id BIGINT AUTO_INCREMENT PRIMARY KEY, ingredient_id BIGINT, metric_id BIGINT)");
        jdbc.execute("CREATE TABLE dish_draft (id BIGINT AUTO_INCREMENT PRIMARY KEY, ingredients VARCHAR(2000))");
        jdbc.execute("CREATE TABLE cooking_record (id BIGINT AUTO_INCREMENT PRIMARY KEY, memo VARCHAR(500))");
        for (String t : List.of("daily_log_item", "menu_cook_usage", "stock_log", "pantry", "pantry_change_log")) {
            jdbc.execute("CREATE TABLE " + t + " (id BIGINT AUTO_INCREMENT PRIMARY KEY, ingredient_id BIGINT)");
        }
    }

    private long count(String sql, Object... args) {
        Long n = jdbc.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }

    @Test
    void 在用检查_列出被哪些表引用() {
        assertThat(refs.usage(9L)).isEmpty();
        jdbc.update("INSERT INTO ingredient_stock(family_id, ingredient_id, level) VALUES (1, 9, 'ENOUGH')");
        jdbc.update("INSERT INTO dish_ingredient(dish_id, ingredient_id) VALUES (1, 9)");
        jdbc.update("INSERT INTO stock_log(ingredient_id) VALUES (9)");
        Map<String, Long> u = refs.usage(9L);
        assertThat(u).containsOnlyKeys("库存", "菜谱", "库存记录");
        assertThat(u.get("菜谱")).isEqualTo(1);
    }

    @Test
    void 合并_无冲突的引用全部改指向目标() {
        jdbc.update("INSERT INTO dish_ingredient(dish_id, ingredient_id) VALUES (1, 10)");
        jdbc.update("INSERT INTO ingredient_stock(family_id, ingredient_id, level) VALUES (1, 10, 'LOW')");
        jdbc.update("INSERT INTO shopping_item(list_id, ingredient_id, unit_id) VALUES (5, 10, 2)");
        jdbc.update("INSERT INTO menu_prep_status(menu_id, ingredient_id) VALUES (3, 10)");
        jdbc.update("INSERT INTO daily_log_item(ingredient_id) VALUES (10)");
        jdbc.update("INSERT INTO menu_cook_usage(ingredient_id) VALUES (10)");
        jdbc.update("INSERT INTO ingredient_nutrition(ingredient_id, metric_id) VALUES (10, 1)");

        MergeStats st = refs.merge(List.of(10L), 99L);

        assertThat(refs.usage(10L)).isEmpty();
        assertThat(refs.usage(99L)).containsKeys("库存", "采购", "菜谱", "备菜", "食记", "做菜记录");
        assertThat(count("SELECT COUNT(*) FROM ingredient_nutrition WHERE ingredient_id = 10")).isZero();
        assertThat(st.totalMoved()).isEqualTo(6);
    }

    @Test
    void 唯一键冲突一_同一道菜已有目标食材_删来源重复行保留目标用量() {
        jdbc.update("INSERT INTO dish_ingredient(dish_id, ingredient_id, amount) VALUES (1, 99, 200)"); // 菜1 已有目标
        jdbc.update("INSERT INTO dish_ingredient(dish_id, ingredient_id, amount) VALUES (1, 10, 50)");  // 菜1 来源 -> 重复
        jdbc.update("INSERT INTO dish_ingredient(dish_id, ingredient_id, amount) VALUES (2, 10, 30)");  // 菜2 无冲突
        jdbc.update("INSERT INTO dish_ingredient(dish_id, ingredient_id, amount) VALUES (2, 11, 70)");  // 菜2 另一来源 -> 与上条合并后冲突

        MergeStats st = refs.merge(List.of(10L, 11L), 99L);

        assertThat(count("SELECT COUNT(*) FROM dish_ingredient WHERE ingredient_id = 99")).isEqualTo(2); // 菜1、菜2 各一行
        assertThat(count("SELECT amount FROM dish_ingredient WHERE dish_id = 1 AND ingredient_id = 99")).isEqualTo(200);
        assertThat(count("SELECT amount FROM dish_ingredient WHERE dish_id = 2 AND ingredient_id = 99")).isEqualTo(30);
        assertThat(count("SELECT COUNT(*) FROM dish_ingredient WHERE ingredient_id IN (10, 11)")).isZero();
        assertThat(st.merged()).containsEntry("dish_ingredient", 2);
    }

    @Test
    void 唯一键冲突二_同一家庭库存已有目标_保留目标行档位取更足() {
        jdbc.update("INSERT INTO ingredient_stock(family_id, ingredient_id, level) VALUES (1, 99, 'LOW')");
        jdbc.update("INSERT INTO ingredient_stock(family_id, ingredient_id, level, best_before) VALUES (1, 10, 'ENOUGH', DATE '2026-12-01')");
        jdbc.update("INSERT INTO ingredient_stock(family_id, ingredient_id, level) VALUES (2, 99, 'ENOUGH')");
        jdbc.update("INSERT INTO ingredient_stock(family_id, ingredient_id, level) VALUES (2, 10, 'NONE')");
        jdbc.update("INSERT INTO ingredient_stock(family_id, ingredient_id, level) VALUES (3, 10, 'LOW')"); // 家庭3 无冲突

        MergeStats st = refs.merge(List.of(10L), 99L);

        assertThat(jdbc.queryForObject("SELECT level FROM ingredient_stock WHERE family_id = 1 AND ingredient_id = 99",
                String.class)).isEqualTo("ENOUGH"); // 来源更足 -> 升档
        assertThat(jdbc.queryForObject("SELECT CAST(best_before AS VARCHAR) FROM ingredient_stock WHERE family_id = 1 AND ingredient_id = 99",
                String.class)).startsWith("2026-12-01"); // 目标没填保质期 -> 补上
        assertThat(jdbc.queryForObject("SELECT level FROM ingredient_stock WHERE family_id = 2 AND ingredient_id = 99",
                String.class)).isEqualTo("ENOUGH"); // 目标更足 -> 不变
        assertThat(count("SELECT COUNT(*) FROM ingredient_stock WHERE family_id = 3 AND ingredient_id = 99")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM ingredient_stock WHERE ingredient_id = 10")).isZero();
        assertThat(st.merged()).containsEntry("ingredient_stock", 2);
    }

    @Test
    void 采购清单与备菜状态冲突_保留目标行删来源行() {
        jdbc.update("INSERT INTO shopping_item(list_id, ingredient_id, unit_id) VALUES (5, 99, 2)");
        jdbc.update("INSERT INTO shopping_item(list_id, ingredient_id, unit_id) VALUES (5, 10, 2)"); // 冲突
        jdbc.update("INSERT INTO shopping_item(list_id, ingredient_id, unit_id) VALUES (5, 10, 3)"); // 单位不同 -> 改指向
        jdbc.update("INSERT INTO menu_prep_status(menu_id, ingredient_id) VALUES (3, 99)");
        jdbc.update("INSERT INTO menu_prep_status(menu_id, ingredient_id) VALUES (3, 10)"); // 冲突

        refs.merge(List.of(10L), 99L);

        assertThat(count("SELECT COUNT(*) FROM shopping_item WHERE ingredient_id = 99")).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM menu_prep_status WHERE ingredient_id = 99")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM shopping_item WHERE ingredient_id = 10")).isZero();
    }

    @Test
    void 合并_菜谱草稿JSON里的食材id改指向目标_名称原样保留() {
        jdbc.update("INSERT INTO dish_draft (id, ingredients) VALUES (1, ?)",
                "[{\"ingredientId\":7,\"ingredientName\":\"洋芋\",\"amountText\":\"2个\"},{\"ingredientId\":70,\"ingredientName\":\"盐\"}]");
        jdbc.update("INSERT INTO dish_draft (id, ingredients) VALUES (2, ?)", "[{\"ingredientId\":\"7\",\"ingredientName\":\"洋芋\"}]");
        jdbc.update("INSERT INTO dish_draft (id, ingredients) VALUES (3, NULL)");

        refs.merge(List.of(7L), 9L);

        String d1 = jdbc.queryForObject("SELECT ingredients FROM dish_draft WHERE id = 1", String.class);
        assertThat(d1).contains("\"ingredientId\":9").contains("\"ingredientId\":70").contains("洋芋").doesNotContain("\"ingredientId\":7,");
        assertThat(jdbc.queryForObject("SELECT ingredients FROM dish_draft WHERE id = 2", String.class)).contains("\"ingredientId\":9");
    }

    @Test
    void 合并_做菜记录用材备注里的食材id改指向目标并去重() {
        jdbc.update("INSERT INTO cooking_record (id, memo) VALUES (1, '用完:7,17;用了一些:9,7')");
        jdbc.update("INSERT INTO cooking_record (id, memo) VALUES (2, '用了一些:77')");

        refs.merge(List.of(7L), 9L);

        assertThat(jdbc.queryForObject("SELECT memo FROM cooking_record WHERE id = 1", String.class)).isEqualTo("用完:9,17;用了一些:9");
        assertThat(jdbc.queryForObject("SELECT memo FROM cooking_record WHERE id = 2", String.class)).isEqualTo("用了一些:77");
    }

    @Test
    void 备注改写纯函数_只换整段id() {
        assertThat(JdbcIngredientReferences.rewriteMemo("用完:1,7,70", 7, 9)).isEqualTo("用完:1,9,70");
        assertThat(JdbcIngredientReferences.rewriteMemo("用完:70", 7, 9)).isNull();
        assertThat(JdbcIngredientReferences.rewriteMemo(null, 7, 9)).isNull();
    }
}
