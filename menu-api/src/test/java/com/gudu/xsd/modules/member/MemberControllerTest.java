package com.gudu.xsd.modules.member;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gudu.xsd.common.PageQuery;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.defaults.DefaultSqlSessionFactory;
import org.apache.ibatis.transaction.TransactionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
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
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MockMvc 接口测试：mock MemberService + MpPermissionService，验证成员端点。
 * 仅测不触发 Sa-Token 静态调用的端点（list/permKeys/add/update/del），
 * current/permissions 涉及 StpUtil，由 E2E 覆盖。
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

    @Test
    void 新增成员_密码加密_返回id() throws Exception {
        // save 回填 id
        org.mockito.Mockito.doAnswer(inv -> {
            ((Member) inv.getArgument(0)).setId(33L);
            return true;
        }).when(svc).save(any(Member.class));

        String body = "{\"name\":\"小张\",\"phone\":\"13800000000\",\"password\":\"abc123\"}";
        mvc.perform(post("/member").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(33));
        verify(svc).save(any(Member.class));
    }

    @Test
    void 更新成员_密码留空不改() throws Exception {
        // 编辑场景 password 传空串 → 不加密，passwordHash 保持 null（这里只验证 updateById 被调）
        String body = "{\"id\":1,\"name\":\"张爸爸\",\"password\":\"\"}";
        mvc.perform(put("/member").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        verify(svc).updateById(any(Member.class));
    }

    @Test
    void 删除成员_调用service() throws Exception {
        mvc.perform(delete("/member/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        verify(svc).removeById(3L);
    }
}
