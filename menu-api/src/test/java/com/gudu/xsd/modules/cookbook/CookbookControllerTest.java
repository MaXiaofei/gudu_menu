package com.gudu.xsd.modules.cookbook;

import com.gudu.xsd.modules.cookbook.CookbookService.DishMatch;
import com.gudu.xsd.modules.dish.Dish;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MockMvc 接口测试：mock CookbookService，验证反向找菜端点 by-ingredients。
 * 范式照 PantryControllerTest：@WebMvcTest + 排除 SaTokenConfig + mock SqlSessionFactory 装配 Mapper bean。
 */
@WebMvcTest(
        value = CookbookController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = com.gudu.xsd.config.SaTokenConfig.class))
@Import(CookbookControllerTest.TestSqlConfig.class)
class CookbookControllerTest {

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
    private CookbookService svc;

    private Dish dish(Long id, String name) {
        Dish d = new Dish();
        d.setId(id);
        d.setName(name);
        return d;
    }

    @Test
    void 反向找菜_返回200_且区分可做与缺食材() throws Exception {
        // 菜1 全匹配可做；菜2 缺「葱」
        given(svc.findDishesByIngredients(any())).willReturn(List.of(
                new DishMatch(dish(1L, "番茄炒蛋"), 2, 2, List.of(), true),
                new DishMatch(dish(2L, "番茄炒蛋加葱"), 2, 3, List.of("葱"), false)));

        mvc.perform(get("/cookbook/by-ingredients").param("ingredientIds", "10,11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].dish.name").value("番茄炒蛋"))
                .andExpect(jsonPath("$.data[0].canMake").value(true))
                .andExpect(jsonPath("$.data[0].matchCount").value(2))
                .andExpect(jsonPath("$.data[1].dish.name").value("番茄炒蛋加葱"))
                .andExpect(jsonPath("$.data[1].canMake").value(false))
                .andExpect(jsonPath("$.data[1].missingIngredients[0]").value("葱"));
    }

    @Test
    void 反向找菜_逗号含空格_也能解析() throws Exception {
        given(svc.findDishesByIngredients(any())).willReturn(List.of());

        mvc.perform(get("/cookbook/by-ingredients").param("ingredientIds", " 10 , 11 ,"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray());
    }
}
