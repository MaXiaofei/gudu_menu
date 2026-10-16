package com.gudu.xsd.modules.ops;

import cn.dev33.satoken.stp.StpUtil;
import com.gudu.xsd.modules.member.AdminChecker;
import com.gudu.xsd.modules.member.AdminGuardAspect;
import com.gudu.xsd.modules.ops.application.OpsStatsAppService;
import com.gudu.xsd.modules.ops.domain.model.OpsStats;
import com.gudu.xsd.modules.ops.interfaces.OpsStatsController;
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
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        value = OpsStatsController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = com.gudu.xsd.config.SaTokenConfig.class))
@Import({OpsStatsControllerTest.TestSqlConfig.class, AdminGuardAspect.class})
@ImportAutoConfiguration(AopAutoConfiguration.class)
class OpsStatsControllerTest {

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
    @MockBean OpsStatsAppService appService;
    @MockBean AdminChecker adminChecker;

    @Test
    void 超管_返回契约结构_默认14天() throws Exception {
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(1L);
            given(adminChecker.isAdmin(1L)).willReturn(true);
            given(appService.stats(14)).willReturn(new OpsStats(
                    new OpsStats.Totals(10, 4, 50, 30, 2),
                    new OpsStats.Last7d(1, 3, 6, 9, new BigDecimal("0.12")),
                    List.of(new OpsStats.TrendPoint(LocalDate.of(2026, 10, 16), 1, 2, 3, 4))));

            mvc.perform(get("/ops/stats"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totals.members").value(10))
                    .andExpect(jsonPath("$.data.totals.feedbackOpen").value(2))
                    .andExpect(jsonPath("$.data.last7d.aiCostYuan").value(0.12))
                    .andExpect(jsonPath("$.data.trend[0].date").value("2026-10-16"))
                    .andExpect(jsonPath("$.data.trend[0].aiCalls").value(4));
        }
    }

    @Test
    void 非超管_403() throws Exception {
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
            given(adminChecker.isAdmin(7L)).willReturn(false);
            mvc.perform(get("/ops/stats").param("days", "7")).andExpect(jsonPath("$.code").value(403));
            verify(appService, never()).stats(anyInt());
        }
    }
}
