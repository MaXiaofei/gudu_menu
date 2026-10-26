package com.gudu.xsd.config;

import com.gudu.xsd.common.tenant.TenantAccessDeniedException;
import com.gudu.xsd.common.tenant.TenantContext;
import com.gudu.xsd.common.tenant.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 租户插件 / 菜谱可见性插件 SQL 改写（multi-tenant-family C.2 / E.1）：纯 jsqlparser，不连库。
 */
class TenantInterceptorSqlTest {

    private final GuduTenantLineInnerInterceptor tenant = new GuduTenantLineInnerInterceptor();
    private final DishVisibilityInnerInterceptor dish = new DishVisibilityInnerInterceptor();

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    private static void as(long familyId) {
        TenantContextHolder.set(new TenantContext(familyId, 1L, 1L, TenantContext.MEMBER));
    }

    @Test
    void 家庭域表_查询自动追加family_id() {
        as(7);
        String sql = tenant.parserSingle("SELECT * FROM menu WHERE id = 10", null);
        assertThat(sql).containsIgnoringCase("family_id = 7").containsIgnoringCase("id = 10");
    }

    @Test
    void 家庭域表_JOIN两张表都加条件() {
        as(7);
        String sql = tenant.parserSingle(
                "SELECT m.id FROM menu m JOIN menu_dish d ON d.menu_id = m.id WHERE m.id = 1", null);
        assertThat(sql).contains("m.family_id = 7").contains("d.family_id = 7");
    }

    @Test
    void 家庭域表_更新删除都加条件_插入补family_id() {
        as(7);
        assertThat(tenant.parserSingle("UPDATE shopping_list SET name = 'x' WHERE id = 3", null))
                .containsIgnoringCase("family_id = 7");
        assertThat(tenant.parserSingle("DELETE FROM review WHERE id = 3", null))
                .containsIgnoringCase("family_id = 7");
        assertThat(tenant.parserSingle("INSERT INTO favorite (member_id, dish_id) VALUES (1, 2)", null))
                .containsIgnoringCase("family_id").contains("7");
    }

    @Test
    void 全局表不过滤() {
        as(7);
        String sql = tenant.parserSingle("SELECT * FROM ingredient WHERE id = 1", null);
        assertThat(sql).doesNotContainIgnoringCase("family_id");
        assertThat(tenant.parserSingle("SELECT * FROM account WHERE id = 1", null))
                .doesNotContainIgnoringCase("family_id");
    }

    @Test
    void 无上下文访问家庭域表_抛异常而非放行() {
        assertThatThrownBy(() -> tenant.parserSingle("SELECT * FROM menu", null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 系统模式_不过滤() {
        String sql = TenantContextHolder.callAsSystem(() -> tenant.parserSingle("SELECT * FROM member", null));
        assertThat(sql).doesNotContainIgnoringCase("family_id");
    }

    @Test
    void 游客_读为空_写拒绝() {
        TenantContextHolder.set(TenantContext.guest());
        assertThat(tenant.parserSingle("SELECT * FROM menu", null)).contains("family_id = -1");
        assertThatThrownBy(() -> tenant.parserSingle("DELETE FROM menu WHERE id = 1", null))
                .isInstanceOf(TenantAccessDeniedException.class);
    }

    @Test
    void 菜谱可见性_公共或本家() {
        as(7);
        String sql = dish.parserSingle("SELECT * FROM dish WHERE id = 1", null);
        assertThat(sql).contains("family_id IS NULL").contains("family_id = 7");
        // 写不改写（权限由 DishService 校验）
        assertThat(dish.parserSingle("UPDATE dish SET name = 'x' WHERE id = 1", null))
                .doesNotContainIgnoringCase("family_id");
        // 非 dish 表不动
        assertThat(dish.parserSingle("SELECT * FROM dish_step WHERE dish_id = 1", null))
                .doesNotContainIgnoringCase("family_id");
    }

    @Test
    void 菜谱可见性_游客只看公共() {
        String sql = dish.parserSingle("SELECT d.id FROM dish d WHERE d.name LIKE '%蛋%'", null);
        assertThat(sql).contains("d.family_id IS NULL").doesNotContain("family_id =");
    }
}
