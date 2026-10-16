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
                classes = com.gudu.xsd.config.SaTokenConfig.class))
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

    @Test
    void 成员分页_返回200_and_分页结构() throws Exception {
        Page<Member> page = new Page<>(1, 10, 1);
        page.setRecords(List.of(member(1L, "张爸爸")));
        org.mockito.Mockito.doReturn(page).when(svc).page(any(PageQuery.class));

        mvc.perform(get("/member").param("pageNum", "1").param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].name").value("张爸爸"));
    }

    @MockBean
    private AdminChecker adminChecker;

    private static final long CALLER = 10L;

    /** 以登录账号 CALLER 身份执行（admin 决定是否超管）。 */
    private void asCaller(boolean admin, ThrowingRunnable body) throws Exception {
        given(adminChecker.isAdmin(CALLER)).willReturn(admin);
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(CALLER);
            stp.when(StpUtil::getSession).thenReturn(org.mockito.Mockito.mock(SaSession.class));
            body.run();
        }
    }

    interface ThrowingRunnable {
        void run() throws Exception;
    }

    private Member account(Long id, int isAdmin) {
        Member m = member(id, "账号" + id);
        m.setPasswordHash("$2a$hash");
        m.setIsAdmin(isAdmin);
        return m;
    }

    @Test
    void 超管新增成员_密码加密_返回id() throws Exception {
        org.mockito.Mockito.doAnswer(inv -> {
            ((Member) inv.getArgument(0)).setId(33L);
            return true;
        }).when(svc).save(any(Member.class));

        asCaller(true, () -> mvc.perform(post("/member").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"小张\",\"phone\":\"13800000000\",\"password\":\"abc123\"}"))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(33)));
        ArgumentCaptor<Member> cap = ArgumentCaptor.forClass(Member.class);
        verify(svc).save(cap.capture());
        assertThat(cap.getValue().getPasswordHash()).startsWith("$2a$");
        assertThat(cap.getValue().getPhone()).isEqualTo("13800000000");
    }

    @Test
    void 普通用户新增成员_只能建纯档案_账号权限字段被丢弃() throws Exception {
        asCaller(false, () -> mvc.perform(post("/member").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"宝宝\",\"phone\":\"admin2\",\"password\":\"x\",\"isAdmin\":1,"
                                + "\"roleTags\":\"32\",\"mpPermissions\":[\"ai.use\"]}"))
                .andExpect(jsonPath("$.code").value(0)));
        ArgumentCaptor<Member> cap = ArgumentCaptor.forClass(Member.class);
        verify(svc).save(cap.capture());
        Member saved = cap.getValue();
        assertThat(saved.getName()).isEqualTo("宝宝");
        assertThat(saved.getIsAdmin()).isNull();
        assertThat(saved.getPasswordHash()).isNull();
        assertThat(saved.getPhone()).isNull();
        assertThat(saved.getRoleTags()).isNull();
        assertThat(saved.getMpPermissions()).isNull();
    }

    @Test
    void 普通用户把自己改成超管_isAdmin被忽略() throws Exception {
        asCaller(false, () -> mvc.perform(put("/member").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":10,\"name\":\"我\",\"isAdmin\":1,\"password\":\"hack\"}"))
                .andExpect(jsonPath("$.code").value(0)));
        ArgumentCaptor<Member> cap = ArgumentCaptor.forClass(Member.class);
        verify(svc).updateById(cap.capture());
        assertThat(cap.getValue().getIsAdmin()).isNull();
        assertThat(cap.getValue().getPasswordHash()).isNull();
    }

    @Test
    void 普通用户改他人账号_403() throws Exception {
        given(svc.getById(1L)).willReturn(account(1L, 1));
        asCaller(false, () -> mvc.perform(put("/member").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":1,\"name\":\"改超管名\"}"))
                .andExpect(jsonPath("$.code").value(403)));
        verify(svc, never()).updateById(any(Member.class));
    }

    @Test
    void 普通用户改纯成员档案_允许() throws Exception {
        given(svc.getById(5L)).willReturn(member(5L, "宝宝"));
        asCaller(false, () -> mvc.perform(put("/member").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":5,\"name\":\"小宝\"}"))
                .andExpect(jsonPath("$.code").value(0)));
        verify(svc).updateById(any(Member.class));
    }

    @Test
    void 普通用户删他人账号_403_删纯档案_允许() throws Exception {
        given(svc.getById(1L)).willReturn(account(1L, 0));
        given(svc.getById(5L)).willReturn(member(5L, "宝宝"));
        asCaller(false, () -> {
            mvc.perform(delete("/member/1")).andExpect(jsonPath("$.code").value(403));
            mvc.perform(delete("/member/5")).andExpect(jsonPath("$.code").value(0));
        });
        verify(svc, never()).removeById(1L);
        verify(svc).removeById(5L);
    }

    @Test
    void 不能删除自己() throws Exception {
        asCaller(true, () -> mvc.perform(delete("/member/10")).andExpect(jsonPath("$.code").value(403)));
        verify(svc, never()).removeById(10L);
    }

    @Test
    void 普通用户切换就餐成员为超管_403() throws Exception {
        given(svc.getById(1L)).willReturn(account(1L, 1));
        asCaller(false, () -> mvc.perform(post("/member/current").param("memberId", "1"))
                .andExpect(jsonPath("$.code").value(403)));
    }

    @Test
    void 普通用户切换为纯档案或本人_允许() throws Exception {
        given(svc.getById(5L)).willReturn(member(5L, "宝宝"));
        asCaller(false, () -> {
            mvc.perform(post("/member/current").param("memberId", "5")).andExpect(jsonPath("$.code").value(0));
            mvc.perform(post("/member/current").param("memberId", "10")).andExpect(jsonPath("$.code").value(0));
        });
    }
}
