package com.gudu.xsd.modules.cookbook.interfaces;

import cn.dev33.satoken.stp.StpUtil;
import com.gudu.xsd.modules.cookbook.application.CookingRecordAppService;
import com.gudu.xsd.modules.cookbook.application.dto.RecentDishView;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.defaults.DefaultSqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mockStatic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        value = CookbookRecentController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = com.gudu.xsd.config.SaTokenConfig.class))
@Import(CookbookRecentControllerTest.TestSqlConfig.class)
class CookbookRecentControllerTest {

    @org.junit.jupiter.api.AfterEach
    void clearTenantContext() {
        com.gudu.xsd.common.tenant.TenantContextHolder.clear();
    }

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
    @MockBean CookingRecordAppService appService;

    @Test
    void 最近做过_当前账号_默认10条_ISO时间() throws Exception {
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(7L); com.gudu.xsd.common.tenant.TenantContextHolder.set(new com.gudu.xsd.common.tenant.TenantContext(1L, 7L, 7L, "MEMBER"));
            given(appService.recent(7L, 10)).willReturn(List.of(new RecentDishView(1L, "番茄炒蛋", null,
                    LocalDateTime.of(2026, 10, 16, 19, 0), 3)));

            mvc.perform(get("/cookbook/recent"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].dishId").value(1))
                    .andExpect(jsonPath("$.data[0].name").value("番茄炒蛋"))
                    .andExpect(jsonPath("$.data[0].cookedCount").value(3))
                    .andExpect(jsonPath("$.data[0].lastCookedAt").value("2026-10-16T19:00:00"));
        }
    }

    @Test
    void 最近做过_自定义limit() throws Exception {
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(7L); com.gudu.xsd.common.tenant.TenantContextHolder.set(new com.gudu.xsd.common.tenant.TenantContext(1L, 7L, 7L, "MEMBER"));
            given(appService.recent(7L, 3)).willReturn(List.of());
            mvc.perform(get("/cookbook/recent").param("limit", "3"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isEmpty());
        }
    }
}
