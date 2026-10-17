package com.gudu.xsd.modules.recommend.interfaces;

import cn.dev33.satoken.stp.StpUtil;
import com.gudu.xsd.modules.pantry.application.dto.CoverageView;
import com.gudu.xsd.modules.pantry.application.dto.ExpiringItemView;
import com.gudu.xsd.modules.recommend.application.HomeRecommendAppService;
import com.gudu.xsd.modules.recommend.application.dto.DishCard;
import com.gudu.xsd.modules.recommend.application.dto.HomeRecommendView;
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
import java.time.LocalDate;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mockStatic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        value = RecommendController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = com.gudu.xsd.config.SaTokenConfig.class))
@Import(RecommendControllerTest.TestSqlConfig.class)
class RecommendControllerTest {

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
    @MockBean HomeRecommendAppService appService;

    @Test
    void 首页智荐_契约结构() throws Exception {
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
            DishCard card = new DishCard(1L, "菠菜炒蛋", "https://img/1.jpg", 15, 2, 3,
                    new CoverageView(1, 2, List.of(new CoverageView.Missing(2L, "鸡蛋"))));
            given(appService.home(7L)).willReturn(new HomeRecommendView(
                    List.of(new ExpiringItemView(5L, "菠菜", "ENOUGH", LocalDate.of(2026, 10, 18), 2, "spinach")),
                    List.of(card), List.of(), List.of()));

            mvc.perform(get("/recommend/home"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.expiring[0].daysLeft").value(2))
                    .andExpect(jsonPath("$.data.expiring[0].iconKey").value("spinach"))
                    .andExpect(jsonPath("$.data.expiringPicks[0].dishId").value(1))
                    .andExpect(jsonPath("$.data.expiringPicks[0].coverUrl").value("https://img/1.jpg"))
                    .andExpect(jsonPath("$.data.expiringPicks[0].coverImage").value("https://img/1.jpg"))
                    .andExpect(jsonPath("$.data.expiringPicks[0].cookMinutes").value(15))
                    .andExpect(jsonPath("$.data.expiringPicks[0].cookedCount").value(3))
                    .andExpect(jsonPath("$.data.expiringPicks[0].coverage.enough").value(1))
                    .andExpect(jsonPath("$.data.expiringPicks[0].coverage.total").value(2))
                    .andExpect(jsonPath("$.data.expiringPicks[0].coverage.missing[0].name").value("鸡蛋"))
                    .andExpect(jsonPath("$.data.expiringPicks[0].coverage.ratio").doesNotExist())
                    .andExpect(jsonPath("$.data.frequent").isArray())
                    .andExpect(jsonPath("$.data.seasonal").isEmpty());
        }
    }
}
