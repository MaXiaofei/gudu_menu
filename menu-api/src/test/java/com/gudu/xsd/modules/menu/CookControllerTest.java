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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** CookController MockMvc 测试（V42）：cook-materials 弹窗数据 + cook 确认 body。照 IngredientUnitGramControllerTest 范式。 */
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
    void GET_确认弹窗数据() throws Exception {
        CookMaterialsVO.Item item = new CookMaterialsVO.Item(10L, "番茄",
                new java.math.BigDecimal("200"), "ENOUGH", false);
        given(cookService.cookMaterials(7L)).willReturn(new CookMaterialsVO(7L, List.of(item)));

        mvc.perform(get("/menu/7/cook-materials"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].ingredientName").value("番茄"))
                .andExpect(jsonPath("$.data.items[0].level").value("ENOUGH"))
                .andExpect(jsonPath("$.data.items[0].isCondiment").value(false));
        verify(cookService).cookMaterials(7L);
    }

    @Test
    void POST_整集做菜确认_带用材body() throws Exception {
        CookResult stub = new CookResult(7L, List.of(1L));
        given(cookService.cookByMenu(eq(7L), any(), eq(List.of(10L)), eq(List.of(20L)))).willReturn(stub);

        mvc.perform(post("/menu/7/cook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usedUp\":[10],\"partiallyUsed\":[20]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.menuId").value(7));
        verify(cookService).cookByMenu(eq(7L), any(), eq(List.of(10L)), eq(List.of(20L)));
    }

    @Test
    void POST_整集做菜确认_无body视为全跳过() throws Exception {
        CookResult stub = new CookResult(7L, List.of(1L));
        given(cookService.cookByMenu(eq(7L), any(), any(), any())).willReturn(stub);

        mvc.perform(post("/menu/7/cook"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.menuId").value(7));
        verify(cookService).cookByMenu(eq(7L), any(), any(), any());
    }
}
