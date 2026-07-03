package com.gudu.xsd.modules.menu;

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
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** CookController MockMvc 测试：照 IngredientUnitGramControllerTest 范式。 */
@WebMvcTest(
        value = CookController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = com.gudu.xsd.config.SaTokenConfig.class))
@Import(CookControllerTest.TestSqlConfig.class)
class CookControllerTest {

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
    @MockBean CookService cookService;

    @Test
    void POST_整集做菜() throws Exception {
        CookResult stub = new CookResult(7L, List.of(), Map.of(), List.of(1L));
        given(cookService.cookByMenu(eq(7L), any())).willReturn(stub);

        mvc.perform(post("/menu/7/cook"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.menuId").value(7));
        verify(cookService).cookByMenu(eq(7L), any());
    }

    @Test
    void POST_单菜直做_带份数() throws Exception {
        given(cookService.cookByDish(eq(3L), eq(new BigDecimal("2")), any()))
                .willReturn(new CookResult(null, List.of(), Map.of(), List.of(9L)));

        mvc.perform(post("/dish/3/cook-now").param("servings", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cookingRecordIds[0]").value(9));
        verify(cookService).cookByDish(eq(3L), eq(new BigDecimal("2")), any());
    }
}
