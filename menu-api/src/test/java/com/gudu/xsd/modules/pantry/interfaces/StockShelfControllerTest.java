package com.gudu.xsd.modules.pantry.interfaces;

import com.gudu.xsd.modules.pantry.application.StockAppService;
import com.gudu.xsd.modules.pantry.application.dto.ExpiringItemView;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.defaults.DefaultSqlSessionFactory;
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
import java.time.LocalDate;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** StockShelfController：临期（三档模型，新结构）+ 保质天数建议。范式照 PantryControllerTest。 */
@WebMvcTest(
        value = StockShelfController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = com.gudu.xsd.config.SaTokenConfig.class))
@Import(StockShelfControllerTest.TestSqlConfig.class)
class StockShelfControllerTest {

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
    @MockBean StockAppService appService;

    @Test
    void 临期_返回ExpiringItem数组_含已过期() throws Exception {
        given(appService.expiring(3)).willReturn(List.of(
                new ExpiringItemView(5L, "牛奶", "LOW", LocalDate.of(2026, 10, 15), -1),
                new ExpiringItemView(1L, "番茄", "ENOUGH", LocalDate.of(2026, 10, 18), 2)));

        mvc.perform(get("/pantry/expiring").param("days", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].ingredientName").value("牛奶"))
                .andExpect(jsonPath("$.data[0].daysLeft").value(-1))
                .andExpect(jsonPath("$.data[1].bestBefore").value("2026-10-18"))
                .andExpect(jsonPath("$.data[1].level").value("ENOUGH"));
    }

    @Test
    void 临期_默认3天() throws Exception {
        given(appService.expiring(3)).willReturn(List.of());
        mvc.perform(get("/pantry/expiring"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void 保质天数建议_有值() throws Exception {
        given(appService.suggestShelfLifeDays(1L)).willReturn(5);
        mvc.perform(get("/pantry/shelf-life").param("ingredientId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.suggestedDays").value(5));
    }

    @Test
    void 保质天数建议_无建议返回null() throws Exception {
        given(appService.suggestShelfLifeDays(2L)).willReturn(null);
        mvc.perform(get("/pantry/shelf-life").param("ingredientId", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.suggestedDays").doesNotExist());
    }

    @Test
    void 余量统计_契约字段() throws Exception {
        given(appService.summary()).willReturn(new com.gudu.xsd.modules.pantry.application.dto.PantrySummaryView(
                24, 6, 3, 3, List.of(new com.gudu.xsd.modules.pantry.application.dto.PantrySummaryView.CategoryCount(10L, "蔬菜", 9))));
        mvc.perform(get("/pantry/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enough").value(24))
                .andExpect(jsonPath("$.data.low").value(6))
                .andExpect(jsonPath("$.data.out").value(3))
                .andExpect(jsonPath("$.data.expiring3d").value(3))
                .andExpect(jsonPath("$.data.byCategory[0].categoryId").value(10))
                .andExpect(jsonPath("$.data.byCategory[0].name").value("蔬菜"))
                .andExpect(jsonPath("$.data.byCategory[0].count").value(9));
    }
}
