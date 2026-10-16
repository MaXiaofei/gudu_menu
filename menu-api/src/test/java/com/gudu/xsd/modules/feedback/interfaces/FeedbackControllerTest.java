package com.gudu.xsd.modules.feedback.interfaces;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gudu.xsd.modules.feedback.application.FeedbackAppService;
import com.gudu.xsd.modules.feedback.application.FeedbackView;
import com.gudu.xsd.modules.member.AdminChecker;
import com.gudu.xsd.modules.member.AdminGuardAspect;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.defaults.DefaultSqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
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
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 意见反馈接口：提交取登录身份；列表/改状态经 AdminGuardAspect（@RequireAdmin）校验超管。 */
@WebMvcTest(
        value = FeedbackController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = com.gudu.xsd.config.SaTokenConfig.class))
@Import({FeedbackControllerTest.TestSqlConfig.class, AdminGuardAspect.class})
@ImportAutoConfiguration(AopAutoConfiguration.class)
class FeedbackControllerTest {

    @TestConfiguration
    static class TestSqlConfig {
        @Bean
        DataSource dataSource() {
            return org.mockito.Mockito.mock(DataSource.class);
        }

        @Bean
        SqlSessionFactory sqlSessionFactory(DataSource ds) {
            return new DefaultSqlSessionFactory(new Configuration(
                    new Environment("test", new JdbcTransactionFactory(), ds)));
        }
    }

    @Autowired MockMvc mvc;
    @MockBean FeedbackAppService appService;
    @MockBean AdminChecker adminChecker;

    @Test
    void 提交_身份取登录账号() throws Exception {
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
            given(appService.submit(7L, "希望能导出菜谱", "wx:abc", List.of("/gudu/uploads/a.jpg"), "mini"))
                    .willReturn(12L);
            mvc.perform(post("/feedback").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"content\":\"希望能导出菜谱\",\"contact\":\"wx:abc\","
                                    + "\"images\":[\"/gudu/uploads/a.jpg\"],\"clientInfo\":\"mini\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").value(12));
        }
    }

    @Test
    void 列表_超管可见() throws Exception {
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(1L);
            given(adminChecker.isAdmin(1L)).willReturn(true);
            Page<FeedbackView> page = new Page<>(1, 10);
            page.setTotal(1);
            page.setRecords(List.of(new FeedbackView(3L, 7L, "张妈妈", "内容够长了", null, List.of(), null, "NEW",
                    LocalDateTime.of(2026, 10, 16, 8, 0))));
            given(appService.page("NEW", 1, 10)).willReturn(page);

            mvc.perform(get("/feedback").param("status", "NEW").param("pageNum", "1").param("pageSize", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.records[0].memberName").value("张妈妈"))
                    .andExpect(jsonPath("$.data.records[0].createTime").value("2026-10-16T08:00:00"))
                    .andExpect(jsonPath("$.data.total").value(1));
        }
    }

    @Test
    void 列表与改状态_非超管403() throws Exception {
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
            given(adminChecker.isAdmin(7L)).willReturn(false);
            mvc.perform(get("/feedback")).andExpect(jsonPath("$.code").value(403));
            mvc.perform(put("/feedback/3/status").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DONE\"}"))
                    .andExpect(jsonPath("$.code").value(403));
            verify(appService, never()).page(any(), any(), any());
            verify(appService, never()).changeStatus(any(), any());
        }
    }

    @Test
    void 改状态_超管() throws Exception {
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(1L);
            given(adminChecker.isAdmin(1L)).willReturn(true);
            mvc.perform(put("/feedback/3/status").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DONE\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));
            verify(appService).changeStatus(3L, "DONE");
        }
    }
}
