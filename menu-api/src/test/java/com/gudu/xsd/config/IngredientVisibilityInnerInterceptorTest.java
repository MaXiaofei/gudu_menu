package com.gudu.xsd.config;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.gudu.xsd.common.security.TenantSession;
import com.gudu.xsd.common.tenant.TenantContext;
import com.gudu.xsd.common.tenant.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/** 食材可见性插件 SQL 改写：公共 + 本家，别家私有读不到；游客 / 后台只看公共；系统模式不过滤。 */
class IngredientVisibilityInnerInterceptorTest {

    private final IngredientVisibilityInnerInterceptor ing = new IngredientVisibilityInnerInterceptor();

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    private static void asFamily(long familyId) {
        TenantContextHolder.set(new TenantContext(familyId, 1L, 1L, TenantContext.MEMBER));
    }

    @Test
    void 家庭用户_看公共加本家_别家私有不在条件里() {
        asFamily(7);
        String sql = ing.parserSingle("SELECT * FROM ingredient WHERE name = '番茄'", null);
        assertThat(sql).containsIgnoringCase("family_id IS NULL").contains("family_id = 7");
    }

    @Test
    void 更新和删除同样带可见性条件_别家私有改不动() {
        asFamily(7);
        assertThat(ing.parserSingle("UPDATE ingredient SET name = 'x' WHERE id = 3", null)).contains("family_id = 7");
        assertThat(ing.parserSingle("DELETE FROM ingredient WHERE id = 3", null)).contains("family_id = 7");
    }

    @Test
    void JOIN里带别名的ingredient也加条件() {
        asFamily(7);
        String sql = ing.parserSingle(
                "SELECT s.id FROM ingredient_stock s JOIN ingredient i ON i.id = s.ingredient_id", null);
        assertThat(sql).contains("i.family_id IS NULL").contains("i.family_id = 7");
        assertThat(sql).doesNotContain("s.family_id");
    }

    @Test
    void 游客无家庭只看公共() {
        String sql = ing.parserSingle("SELECT * FROM ingredient", null);
        assertThat(sql).containsIgnoringCase("family_id IS NULL").doesNotContain("family_id =");
    }

    @Test
    void 别名表和其他表不受影响() {
        asFamily(7);
        assertThat(ing.parserSingle("SELECT * FROM ingredient_alias WHERE alias = 'x'", null))
                .doesNotContain("family_id");
        assertThat(ing.parserSingle("SELECT * FROM ingredient_stock", null)).doesNotContain("family_id");
    }

    @Test
    void 系统模式不过滤_运营收编用() {
        asFamily(7);
        String sql = TenantContextHolder.callAsSystem(() -> ing.parserSingle("SELECT * FROM ingredient", null));
        assertThat(sql).doesNotContain("family_id");
    }

    @Test
    void 后台登录_即使运营自己有家庭也只看公共食材() {
        asFamily(7);
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            SaSession s = mock(SaSession.class);
            when(s.get(TenantSession.LOGIN_SCOPE)).thenReturn(TenantSession.SCOPE_ADMIN);
            stp.when(StpUtil::isLogin).thenReturn(true);
            stp.when(StpUtil::getTokenSession).thenReturn(s);
            String sql = ing.parserSingle("SELECT * FROM ingredient", null);
            assertThat(sql).containsIgnoringCase("family_id IS NULL").doesNotContain("family_id = 7");
        }
    }
}
