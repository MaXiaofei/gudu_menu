package com.gudu.xsd.modules.nutrition.interfaces;

import cn.dev33.satoken.stp.StpUtil;
import com.gudu.xsd.modules.nutrition.application.IngredientFrequencyAppService;
import com.gudu.xsd.modules.nutrition.application.IngredientFrequencyAppService.FrequentIngredient;
import com.gudu.xsd.modules.nutrition.IngredientService;
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
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mockStatic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** /ingredient/frequent 与 IngredientController 的 /ingredient/{id} 共存（字面量路径优先）。 */
@WebMvcTest(
        value = {IngredientFrequentController.class, com.gudu.xsd.modules.nutrition.IngredientController.class},
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = com.gudu.xsd.config.SaTokenConfig.class))
@Import(IngredientFrequentControllerTest.TestSqlConfig.class)
class IngredientFrequentControllerTest {

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
    @MockBean IngredientFrequencyAppService appService;
    @MockBean IngredientService ingredientService;

    @Test
    void 常用食材_默认8个_当前账号() throws Exception {
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
            given(appService.frequent(7L, 8)).willReturn(List.of(new FrequentIngredient(1L, "番茄")));
            mvc.perform(get("/ingredient/frequent"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].id").value(1))
                    .andExpect(jsonPath("$.data[0].name").value("番茄"));
        }
    }
}
