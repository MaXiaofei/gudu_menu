package com.gudu.xsd.config;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gudu.xsd.common.BizException;
import com.gudu.xsd.common.security.TenantSession;
import com.gudu.xsd.common.tenant.DishVisibility;
import com.gudu.xsd.common.tenant.TenantContext;
import com.gudu.xsd.common.tenant.TenantContextHolder;
import com.gudu.xsd.modules.dish.Dish;
import com.gudu.xsd.modules.dish.DishAccessGuard;
import com.gudu.xsd.modules.identity.application.AccountQueryApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * 管理后台公共菜谱库（multi-tenant-family F.3）：后台（scope=admin）登录的运营即使自己有家庭，
 * 菜谱查询也只看 family_id IS NULL 的公共菜谱，写操作只允许改公共菜谱，家庭私有菜谱不可见 / 不可写。
 * 小程序（非 admin scope）仍是「公共 + 本家」。
 */
class DishVisibilityAdminScopeTest {

    private final DishVisibilityInnerInterceptor dish = new DishVisibilityInnerInterceptor();

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    private static void asFamily(long familyId) {
        TenantContextHolder.set(new TenantContext(familyId, 1L, 1L, TenantContext.OWNER));
    }

    /** 模拟登录会话；adminScope=true 为管理后台登录。 */
    private static MockedStatic<StpUtil> session(boolean adminScope) {
        MockedStatic<StpUtil> stp = mockStatic(StpUtil.class);
        SaSession s = mock(SaSession.class);
        when(s.get(TenantSession.LOGIN_SCOPE)).thenReturn(adminScope ? TenantSession.SCOPE_ADMIN : null);
        stp.when(StpUtil::isLogin).thenReturn(true);
        stp.when(StpUtil::getTokenSession).thenReturn(s);
        stp.when(StpUtil::getLoginIdAsLong).thenReturn(1L);
        return stp;
    }

    @Test
    void 后台登录_SQL只看公共菜谱_子表也只认公共() {
        asFamily(7);
        try (MockedStatic<StpUtil> ignored = session(true)) {
            String sql = dish.parserSingle("SELECT * FROM dish WHERE id = 3", null);
            assertThat(sql).containsIgnoringCase("family_id IS NULL").doesNotContain("family_id = 7");
            String child = dish.parserSingle("SELECT * FROM dish_ingredient WHERE dish_id = 3", null);
            assertThat(child).contains("vd.family_id IS NULL").doesNotContain("family_id = 7");
            String upd = dish.parserSingle("UPDATE dish SET name = 'x' WHERE id = 3", null);
            assertThat(upd).containsIgnoringCase("family_id IS NULL").doesNotContain("family_id = 7");
        }
    }

    @Test
    void 小程序登录_SQL仍是公共加本家() {
        asFamily(7);
        try (MockedStatic<StpUtil> ignored = session(false)) {
            String sql = dish.parserSingle("SELECT * FROM dish WHERE id = 3", null);
            assertThat(sql).containsIgnoringCase("family_id IS NULL").contains("family_id = 7");
        }
    }

    @Test
    void 后台登录_查询条件与可见_写权限只认公共() {
        asFamily(7);
        try (MockedStatic<StpUtil> ignored = session(true)) {
            QueryWrapper<Dish> q = DishVisibility.visible(new QueryWrapper<>());
            assertThat(q.getSqlSegment()).containsIgnoringCase("IS NULL");
            assertThat(q.getParamNameValuePairs()).isEmpty();
            assertThat(DishVisibility.isVisible(null)).isTrue();
            assertThat(DishVisibility.isVisible(7L)).as("自己家的私有菜谱在后台也不可见").isFalse();
            assertThat(DishVisibility.isWritable(7L, true)).as("私有菜谱后台不可写").isFalse();
            assertThat(DishVisibility.isWritable(null, true)).isTrue();
            assertThat(DishVisibility.isWritable(null, false)).isFalse();
        }
    }

    @Test
    void 小程序登录_本家私有菜谱仍可见可写() {
        asFamily(7);
        try (MockedStatic<StpUtil> ignored = session(false)) {
            assertThat(DishVisibility.isVisible(7L)).isTrue();
            assertThat(DishVisibility.isWritable(7L, false)).isTrue();
            assertThat(DishVisibility.isWritable(9L, true)).isFalse();
        }
    }

    @Test
    void 后台登录_运营改私有菜谱404_改公共菜谱放行() {
        asFamily(7);
        AccountQueryApi accounts = mock(AccountQueryApi.class);
        when(accounts.isOperator(1L)).thenReturn(true);
        DishAccessGuard guard = new DishAccessGuard(accounts);
        Dish priv = new Dish();
        priv.setFamilyId(7L);
        Dish pub = new Dish();
        try (MockedStatic<StpUtil> ignored = session(true)) {
            assertThatThrownBy(() -> guard.checkWritable(priv)).isInstanceOf(BizException.class)
                    .hasMessageContaining("不存在");
            guard.checkWritable(pub);
        }
        try (MockedStatic<StpUtil> ignored = session(false)) {
            guard.checkWritable(priv);
        }
    }
}
