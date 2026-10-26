package com.gudu.xsd.modules.member;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gudu.xsd.common.PageQuery;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.defaults.DefaultSqlSessionFactory;
import org.apache.ibatis.transaction.TransactionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MockMvc 接口测试：mock MemberService + MpPermissionService，验证成员端点。
 * 写接口用 MockedStatic 桩 StpUtil 登录身份，覆盖字段级权限与越权拒绝（security-hardening S2/S3）。
 * 范式照 PantryControllerTest。
 */
@WebMvcTest(
        value = MemberController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {com.gudu.xsd.config.SaTokenConfig.class,
                        com.gudu.xsd.modules.family.interfaces.TenantWebConfig.class}))
@Import(MemberControllerTest.TestSqlConfig.class)
class MemberControllerTest {

    @TestConfiguration
    static class TestSqlConfig {
        @Bean
        DataSource dataSource() {
            return org.mockito.Mockito.mock(DataSource.class);
        }

        @Bean
        SqlSessionFactory sqlSessionFactory(DataSource ds) {
            TransactionFactory tx = new JdbcTransactionFactory();
            Environment env = new Environment("test", tx, ds);
            Configuration cfg = new Configuration(env);
            return new DefaultSqlSessionFactory(cfg);
        }
    }

    @Autowired
    private MockMvc mvc;

    @MockBean
    private MemberService svc;

    @MockBean
    private MpPermissionService permSvc;

    private Member member(Long id, String name) {
        Member m = new Member();
        m.setId(id);
        m.setName(name);
        return m;
    }

    @MockBean
    private AdminChecker adminChecker;

    @MockBean
    private com.gudu.xsd.modules.family.application.FamilyAppService familyApp;

    @MockBean
    private com.gudu.xsd.modules.family.application.FamilyQueryApi familyQuery;

    private static final long CALLER = 10L;
    private static final long FAMILY = 3L;

    /**
     * 以登录账号 CALLER 身份、在家庭 FAMILY 中以 role 角色执行（admin 决定是否平台超管）。
     * member 表按家庭过滤由租户插件保证：这里 svc.getById 返回 null 即模拟「别家 / 不存在」。
     */
    private void asCaller(boolean admin, String role, ThrowingRunnable body) throws Exception {
        given(adminChecker.isAdmin(CALLER)).willReturn(admin);
        com.gudu.xsd.common.tenant.TenantContextHolder.set(
                new com.gudu.xsd.common.tenant.TenantContext(FAMILY, CALLER, 100L, role));
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(CALLER);
            stp.when(StpUtil::isLogin).thenReturn(true);
            stp.when(StpUtil::getTokenSession).thenReturn(org.mockito.Mockito.mock(SaSession.class));
            body.run();
        } finally {
            com.gudu.xsd.common.tenant.TenantContextHolder.clear();
        }
    }

    interface ThrowingRunnable {
        void run() throws Exception;
    }

    /** 有账号的成员档案（accountId 关联）。 */
    private Member linked(Long id, long accountId) {
        Member m = member(id, "账号" + accountId);
        m.setAccountId(accountId);
        return m;
    }

    @Test
    void 成员分页_只返回本家_不输出手机号与openid() throws Exception {
        Member m = member(1L, "张爸爸");
        m.setPhone("13800000001");
        m.setOpenid("oid-secret");
        Page<Member> page = new Page<>(1, 10, 1);
        page.setRecords(List.of(m));
        org.mockito.Mockito.doReturn(page).when(svc).page(any(PageQuery.class));

        asCaller(false, "MEMBER", () -> mvc.perform(get("/member").param("pageNum", "1").param("pageSize", "10"))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].name").value("张爸爸"))
                .andExpect(jsonPath("$.data.records[0].phone").doesNotExist())
                .andExpect(jsonPath("$.data.records[0].openid").doesNotExist())
                .andExpect(jsonPath("$.data.records[0].passwordHash").doesNotExist()));
    }

    @Test
    void 主理人新增档案_账号字段被丢弃() throws Exception {
        asCaller(false, "OWNER", () -> mvc.perform(post("/member").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"宝宝\",\"phone\":\"13800000009\",\"password\":\"x\",\"isAdmin\":1,"
                                + "\"roleTags\":\"32\"}"))
                .andExpect(jsonPath("$.code").value(0)));
        ArgumentCaptor<Member> cap = ArgumentCaptor.forClass(Member.class);
        verify(svc).save(cap.capture());
        Member saved = cap.getValue();
        assertThat(saved.getName()).isEqualTo("宝宝");
        assertThat(saved.getIsAdmin()).isNull();
        assertThat(saved.getPasswordHash()).isNull();
        assertThat(saved.getPhone()).isNull();
        assertThat(saved.getRoleTags()).isEqualTo("32");
        verify(familyApp, never()).registerPhoneAccount(any(), any(), any(), any());
    }

    @Test
    void 普通成员新增档案_403() throws Exception {
        asCaller(false, "MEMBER", () -> mvc.perform(post("/member").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"宝宝\"}"))
                .andExpect(jsonPath("$.code").value(403)));
        verify(svc, never()).save(any(Member.class));
    }

    @Test
    void 超管带手机号密码新增_代建账号并加入当前家庭_返回档案id() throws Exception {
        given(familyApp.registerPhoneAccount("13800000000", "abc123", "小张", FAMILY)).willReturn(55L);
        given(familyQuery.profileIdOf(FAMILY, 55L)).willReturn(java.util.Optional.of(66L));
        asCaller(true, "OWNER", () -> mvc.perform(post("/member").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"小张\",\"phone\":\"13800000000\",\"password\":\"abc123\"}"))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(66)));
        ArgumentCaptor<Member> cap = ArgumentCaptor.forClass(Member.class);
        verify(svc).updateById(cap.capture());
        assertThat(cap.getValue().getId()).isEqualTo(66L);
        assertThat(cap.getValue().getPasswordHash()).isNull();
    }

    @Test
    void 普通成员改本人档案_允许_越权字段被忽略() throws Exception {
        given(svc.getById(100L)).willReturn(linked(100L, CALLER));
        asCaller(false, "MEMBER", () -> mvc.perform(put("/member").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":100,\"name\":\"我\",\"isAdmin\":1,\"roleTags\":\"32\"}"))
                .andExpect(jsonPath("$.code").value(0)));
        ArgumentCaptor<Member> cap = ArgumentCaptor.forClass(Member.class);
        verify(svc).updateById(cap.capture());
        assertThat(cap.getValue().getIsAdmin()).isNull();
        assertThat(cap.getValue().getRoleTags()).isNull();
    }

    @Test
    void 普通成员改他人或宝宝档案_403() throws Exception {
        given(svc.getById(1L)).willReturn(linked(1L, 1L));
        given(svc.getById(5L)).willReturn(member(5L, "宝宝"));
        asCaller(false, "MEMBER", () -> {
            mvc.perform(put("/member").contentType(MediaType.APPLICATION_JSON).content("{\"id\":1,\"name\":\"x\"}"))
                    .andExpect(jsonPath("$.code").value(403));
            mvc.perform(put("/member").contentType(MediaType.APPLICATION_JSON).content("{\"id\":5,\"name\":\"x\"}"))
                    .andExpect(jsonPath("$.code").value(403));
        });
        verify(svc, never()).updateById(any(Member.class));
    }

    @Test
    void 别家成员_改删切换查权限营养_一律404() throws Exception {
        given(svc.getById(77L)).willReturn(null); // 租户过滤：别家档案查不到
        asCaller(true, "OWNER", () -> {
            mvc.perform(put("/member").contentType(MediaType.APPLICATION_JSON).content("{\"id\":77,\"name\":\"x\"}"))
                    .andExpect(jsonPath("$.code").value(404));
            mvc.perform(delete("/member/77")).andExpect(jsonPath("$.code").value(404));
            mvc.perform(post("/member/current").param("memberId", "77")).andExpect(jsonPath("$.code").value(404));
            mvc.perform(get("/member/77/permissions")).andExpect(jsonPath("$.code").value(404));
            mvc.perform(get("/member/77/nutrition-target")).andExpect(jsonPath("$.data").doesNotExist());
        });
        verify(svc, never()).updateById(any(Member.class));
        verify(svc, never()).removeById(77L);
        verify(familyQuery).nutritionTarget(FAMILY, 77L);
    }

    @Test
    void 管理员删宝宝档案_允许_删有账号成员_403_删自己_403() throws Exception {
        given(svc.getById(5L)).willReturn(member(5L, "宝宝"));
        given(svc.getById(1L)).willReturn(linked(1L, 1L));
        given(svc.getById(100L)).willReturn(linked(100L, CALLER));
        asCaller(false, "ADMIN", () -> {
            mvc.perform(delete("/member/5")).andExpect(jsonPath("$.code").value(0));
            mvc.perform(delete("/member/1")).andExpect(jsonPath("$.code").value(403));
            mvc.perform(delete("/member/100")).andExpect(jsonPath("$.code").value(403));
        });
        verify(svc).removeById(5L);
        verify(svc, never()).removeById(1L);
    }

    @Test
    void 普通成员删档案_403() throws Exception {
        given(svc.getById(5L)).willReturn(member(5L, "宝宝"));
        asCaller(false, "MEMBER", () -> mvc.perform(delete("/member/5")).andExpect(jsonPath("$.code").value(403)));
        verify(svc, never()).removeById(5L);
    }

    @Test
    void 普通成员切换就餐成员_本人或宝宝允许_其他账号403() throws Exception {
        given(svc.getById(5L)).willReturn(member(5L, "宝宝"));
        given(svc.getById(100L)).willReturn(linked(100L, CALLER));
        given(svc.getById(1L)).willReturn(linked(1L, 1L));
        asCaller(false, "MEMBER", () -> {
            mvc.perform(post("/member/current").param("memberId", "5")).andExpect(jsonPath("$.code").value(0));
            mvc.perform(post("/member/current").param("memberId", "100")).andExpect(jsonPath("$.code").value(0));
            mvc.perform(post("/member/current").param("memberId", "1")).andExpect(jsonPath("$.code").value(403));
        });
    }
}
