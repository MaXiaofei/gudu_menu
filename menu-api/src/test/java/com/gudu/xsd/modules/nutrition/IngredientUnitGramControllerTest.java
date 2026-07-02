package com.gudu.xsd.modules.nutrition;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.math.BigDecimal;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 换算接口 MockMvc：照 PantryControllerTest 范式（排除 SaTokenConfig + mock service +
 * H2/mock SqlSessionFactory 装配 Mapper bean）。
 */
@WebMvcTest(
        value = IngredientController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = com.gudu.xsd.config.SaTokenConfig.class))
@Import(IngredientUnitGramControllerTest.TestSqlConfig.class)
class IngredientUnitGramControllerTest {

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
    MockMvc mvc;
    @MockBean
    IngredientService svc;
    private final ObjectMapper om = new ObjectMapper();

    @Test
    void GET_食材换算列表() throws Exception {
        IngredientUnitGram g = new IngredientUnitGram();
        g.setIngredientId(1L);
        g.setUnitId(2L);
        g.setGramsPerUnit(new BigDecimal("50"));
        g.setIsDefault(1);
        given(svc.listUnitGrams(1L)).willReturn(List.of(g));

        mvc.perform(get("/ingredient/1/unit-grams"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].gramsPerUnit").value(50));
    }

    @Test
    void PUT_替换食材换算() throws Exception {
        IngredientUnitGram g = new IngredientUnitGram();
        g.setUnitId(2L);
        g.setGramsPerUnit(new BigDecimal("55"));
        g.setIsDefault(1);
        mvc.perform(put("/ingredient/1/unit-grams")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(List.of(g))))
                .andExpect(status().isOk());
        verify(svc).replaceUnitGrams(1L, List.of(g));
    }
}
