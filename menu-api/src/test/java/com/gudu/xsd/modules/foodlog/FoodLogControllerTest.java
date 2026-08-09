package com.gudu.xsd.modules.foodlog;

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
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** FoodLogController MockMvc 测试。照 IngredientUnitGramControllerTest 范式。 */
@WebMvcTest(
        value = FoodLogController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = com.gudu.xsd.config.SaTokenConfig.class))
@Import(FoodLogControllerTest.TestSqlConfig.class)
class FoodLogControllerTest {

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
            return new DefaultSqlSessionFactory(new Configuration(env));
        }
    }

    @Autowired MockMvc mvc;
    @MockBean FoodLogService svc;

    @Test
    void GET_月视图_统计卡和时间轴() throws Exception {
        FoodLogService.Summary summary = new FoodLogService.Summary(18, 42, 12, List.of("番茄炒蛋"));
        FoodLogService.Meal meal = new FoodLogService.Meal(10L, "今晚的饭",
                LocalDateTime.of(2026, 7, 2, 19, 20), 3, 2,
                List.of("番茄炒蛋", "清蒸鲈鱼"), 2, 1, false);
        given(svc.month(any(), eq(2026), eq(7), eq(null), eq(null), eq(null), eq(1), eq(15)))
                .willReturn(new FoodLogService.MonthVO(summary, List.of(meal), 1, 1, 15));

        mvc.perform(get("/food-log/month").param("month", "2026-07"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.meals").value(18))
                .andExpect(jsonPath("$.data.summary.cookDays").value(12))
                .andExpect(jsonPath("$.data.records[0].name").value("今晚的饭"))
                .andExpect(jsonPath("$.data.records[0].usedUpCount").value(2));
        verify(svc).month(any(), eq(2026), eq(7), eq(null), eq(null), eq(null), eq(1), eq(15));
    }

    @Test
    void GET_按菜汇总() throws Exception {
        FoodLogService.Item item = new FoodLogService.Item(1L, "番茄炒蛋", 6,
                LocalDateTime.of(2026, 7, 2, 19, 0), 4.5);
        given(svc.byDish(any(), eq(2026), eq(7), eq(null), eq(null), eq(null)))
                .willReturn(new FoodLogService.ByDishVO(1, List.of(item)));

        mvc.perform(get("/food-log/by-dish").param("month", "2026-07"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalKinds").value(1))
                .andExpect(jsonPath("$.data.items[0].count").value(6))
                .andExpect(jsonPath("$.data.items[0].avgStar").value(4.5));
    }

    @Test
    void GET_年视图() throws Exception {
        given(svc.year(any(), eq(2026))).willReturn(new FoodLogService.YearVO(2026, new int[]{1, 0, 2, 0, 0, 0, 3, 0, 0, 0, 0, 0}));

        mvc.perform(get("/food-log/year").param("year", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.monthCounts[0]").value(1))
                .andExpect(jsonPath("$.data.monthCounts[6]").value(3));
    }

    @Test
    void GET_单条详情() throws Exception {
        FoodLogService.DishItem d = new FoodLogService.DishItem(1L, "番茄炒蛋", new java.math.BigDecimal("2"), null);
        given(svc.detail(any(), eq(10L))).willReturn(new FoodLogService.DetailVO(
                10L, "今晚的饭", LocalDateTime.of(2026, 7, 2, 19, 20), 2,
                List.of(d), List.of("番茄"), List.of("盐"), false));

        mvc.perform(get("/food-log/detail").param("menuId", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.usedUp[0]").value("番茄"))
                .andExpect(jsonPath("$.data.dishes[0].dishName").value("番茄炒蛋"));
    }

    @Test
    void GET_月份格式错误_报错() throws Exception {
        mvc.perform(get("/food-log/month").param("month", "2026-13"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));
    }
}
