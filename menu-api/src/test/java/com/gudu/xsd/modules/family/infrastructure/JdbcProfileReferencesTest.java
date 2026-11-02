package com.gudu.xsd.modules.family.infrastructure;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 档案引用改指与家庭业务数据探测（H2 MySQL 模式，建表对照 menu-api/sql 的真实列名）：
 * 合并重复档案时按档案归属的数据由 from 改指 to；favorite 有 UNIQUE(member_id, dish_id) 要先去重。
 */
class JdbcProfileReferencesTest {

    private JdbcTemplate jdbc;
    private JdbcProfileReferences refs;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource ds = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(ds);
        refs = new JdbcProfileReferences(ds);
        jdbc.execute("CREATE TABLE cooking_record (id BIGINT AUTO_INCREMENT PRIMARY KEY, family_id BIGINT, member_id BIGINT)");
        jdbc.execute("CREATE TABLE review (id BIGINT AUTO_INCREMENT PRIMARY KEY, family_id BIGINT, member_id BIGINT, deleted TINYINT DEFAULT 0)");
        jdbc.execute("CREATE TABLE favorite (id BIGINT AUTO_INCREMENT PRIMARY KEY, family_id BIGINT, member_id BIGINT, dish_id BIGINT, UNIQUE (member_id, dish_id))");
        jdbc.execute("CREATE TABLE daily_log (id BIGINT AUTO_INCREMENT PRIMARY KEY, family_id BIGINT, member_id BIGINT, deleted TINYINT DEFAULT 0)");
        jdbc.execute("CREATE TABLE menu (id BIGINT AUTO_INCREMENT PRIMARY KEY, family_id BIGINT, target_member_id BIGINT, deleted TINYINT DEFAULT 0)");
        jdbc.execute("CREATE TABLE dish_draft (id BIGINT AUTO_INCREMENT PRIMARY KEY, family_id BIGINT, user_id BIGINT)");
        jdbc.execute("CREATE TABLE meal_plan_draft (id BIGINT AUTO_INCREMENT PRIMARY KEY, family_id BIGINT, member_id BIGINT)");
        jdbc.execute("CREATE TABLE ai_call_log (id BIGINT AUTO_INCREMENT PRIMARY KEY, member_id BIGINT)");
        jdbc.execute("CREATE TABLE notification (id BIGINT AUTO_INCREMENT PRIMARY KEY, recipient_account_id BIGINT, recipient_profile_id BIGINT)");
        // 仅用于「有无业务数据」探测的其余家庭域表
        for (String t : new String[]{"menu_template", "ingredient_stock", "meal_plan"}) {
            jdbc.execute("CREATE TABLE " + t + " (id BIGINT AUTO_INCREMENT PRIMARY KEY, family_id BIGINT"
                    + ("meal_plan".equals(t) ? ", deleted TINYINT DEFAULT 0" : "") + ")");
        }
        for (String t : new String[]{"pantry", "shopping_list"}) {
            jdbc.execute("CREATE TABLE " + t + " (id BIGINT AUTO_INCREMENT PRIMARY KEY, family_id BIGINT, deleted TINYINT DEFAULT 0)");
        }
        jdbc.execute("CREATE TABLE dish (id BIGINT AUTO_INCREMENT PRIMARY KEY, family_id BIGINT, deleted TINYINT DEFAULT 0)");
        jdbc.execute("CREATE TABLE ingredient (id BIGINT AUTO_INCREMENT PRIMARY KEY, family_id BIGINT, deleted TINYINT DEFAULT 0)");
    }

    private long count(String sql, Object... args) {
        Long n = jdbc.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }

    @Test
    void 改指_各表按档案归属的列由from改为to_不动别人的() {
        jdbc.update("INSERT INTO cooking_record(family_id, member_id) VALUES (1, 22), (1, 99)");
        jdbc.update("INSERT INTO review(family_id, member_id) VALUES (1, 22)");
        jdbc.update("INSERT INTO daily_log(family_id, member_id) VALUES (1, 22)");
        jdbc.update("INSERT INTO menu(family_id, target_member_id) VALUES (1, 22)");
        jdbc.update("INSERT INTO dish_draft(family_id, user_id) VALUES (1, 22)");
        jdbc.update("INSERT INTO meal_plan_draft(family_id, member_id) VALUES (1, 22)");
        jdbc.update("INSERT INTO ai_call_log(member_id) VALUES (22)");
        jdbc.update("INSERT INTO notification(recipient_account_id, recipient_profile_id) VALUES (NULL, 22), (22, NULL)");

        refs.reassign(22L, 12L);

        assertThat(count("SELECT COUNT(*) FROM cooking_record WHERE member_id = 12")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM cooking_record WHERE member_id = 99")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM review WHERE member_id = 12")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM daily_log WHERE member_id = 12")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM menu WHERE target_member_id = 12")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM dish_draft WHERE user_id = 12")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM meal_plan_draft WHERE member_id = 12")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM ai_call_log WHERE member_id = 12")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM notification WHERE recipient_profile_id = 12")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM notification WHERE recipient_account_id = 22"))
                .as("账号级通知不是档案 id，不能被误改").isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM cooking_record WHERE member_id = 22")).isZero();
    }

    @Test
    void 改指_收藏唯一键冲突_同一道菜两边都收藏则保留to的_去掉from的() {
        jdbc.update("INSERT INTO favorite(family_id, member_id, dish_id) VALUES (1, 22, 5), (1, 22, 6), (1, 12, 5)");

        refs.reassign(22L, 12L);

        assertThat(count("SELECT COUNT(*) FROM favorite WHERE member_id = 12")).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM favorite WHERE member_id = 22")).isZero();
        assertThat(count("SELECT COUNT(*) FROM favorite WHERE member_id = 12 AND dish_id = 6")).isEqualTo(1);
    }

    @Test
    void 改指_重复执行幂等() {
        jdbc.update("INSERT INTO cooking_record(family_id, member_id) VALUES (1, 22)");
        refs.reassign(22L, 12L);
        refs.reassign(22L, 12L);
        assertThat(count("SELECT COUNT(*) FROM cooking_record WHERE member_id = 12")).isEqualTo(1);
    }

    @Test
    void 业务数据探测_全空为false_任一表有数据为true_软删数据不算() {
        assertThat(refs.familyHasBusinessData(1L)).isFalse();

        jdbc.update("INSERT INTO menu(family_id, target_member_id, deleted) VALUES (1, NULL, 1)");
        jdbc.update("INSERT INTO dish(family_id, deleted) VALUES (1, 1)");
        assertThat(refs.familyHasBusinessData(1L)).as("软删除的不算").isFalse();

        jdbc.update("INSERT INTO ingredient_stock(family_id) VALUES (2)");
        assertThat(refs.familyHasBusinessData(1L)).as("别家的不算").isFalse();

        jdbc.update("INSERT INTO ingredient_stock(family_id) VALUES (1)");
        assertThat(refs.familyHasBusinessData(1L)).isTrue();
    }

    @Test
    void 业务数据探测_覆盖菜谱_食材_做菜记录等各表() {
        for (String sql : new String[]{
                "INSERT INTO dish(family_id, deleted) VALUES (1, 0)",
                "INSERT INTO ingredient(family_id, deleted) VALUES (1, 0)",
                "INSERT INTO cooking_record(family_id, member_id) VALUES (1, 1)",
                "INSERT INTO shopping_list(family_id, deleted) VALUES (1, 0)",
                "INSERT INTO pantry(family_id, deleted) VALUES (1, 0)",
                "INSERT INTO menu(family_id, target_member_id, deleted) VALUES (1, NULL, 0)",
                "INSERT INTO daily_log(family_id, member_id, deleted) VALUES (1, 1, 0)",
                "INSERT INTO review(family_id, member_id, deleted) VALUES (1, 1, 0)",
                "INSERT INTO favorite(family_id, member_id, dish_id) VALUES (1, 1, 1)",
                "INSERT INTO meal_plan(family_id, deleted) VALUES (1, 0)",
                "INSERT INTO menu_template(family_id) VALUES (1)",
                "INSERT INTO dish_draft(family_id, user_id) VALUES (1, 1)",
                "INSERT INTO meal_plan_draft(family_id, member_id) VALUES (1, 1)"}) {
            jdbc.update("DELETE FROM dish");
            jdbc.update("DELETE FROM ingredient");
            jdbc.update("DELETE FROM cooking_record");
            jdbc.update("DELETE FROM shopping_list");
            jdbc.update("DELETE FROM pantry");
            jdbc.update("DELETE FROM menu");
            jdbc.update("DELETE FROM daily_log");
            jdbc.update("DELETE FROM review");
            jdbc.update("DELETE FROM favorite");
            jdbc.update("DELETE FROM meal_plan");
            jdbc.update("DELETE FROM menu_template");
            jdbc.update("DELETE FROM dish_draft");
            jdbc.update("DELETE FROM meal_plan_draft");
            jdbc.update(sql);
            assertThat(refs.familyHasBusinessData(1L)).as(sql).isTrue();
        }
    }
}
