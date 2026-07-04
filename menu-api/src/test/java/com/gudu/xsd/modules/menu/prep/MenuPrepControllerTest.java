package com.gudu.xsd.modules.menu.prep;

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
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MockMvc 接口测试：mock MenuPrepService，验证备菜端点。
 * 范式照 {@link com.gudu.xsd.modules.menu.MenuControllerTest}。
 */
@WebMvcTest(
        value = MenuPrepController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = com.gudu.xsd.config.SaTokenConfig.class))
@Import(MenuPrepControllerTest.TestSqlConfig.class)
class MenuPrepControllerTest {

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
    private MenuPrepService svc;

    private final ObjectMapper om = new ObjectMapper();

    /** GET /menu/{id}/prep：返回 items + condiments + 进度。 */
    @Test
    void 备菜列表_返回200_and_items_进度() throws Exception {
        MenuPrepVO vo = new MenuPrepVO(
                List.of(new PrepItemVO(1L, "番茄", new BigDecimal("300"), 2,
                        List.of("番茄炒蛋", "番茄汤"), "READY", true)),
                List.of(new PrepItemVO(16L, "食用油", new BigDecimal("30"), 1,
                        List.of("番茄炒蛋"), "PENDING", false)),
                1, 1);
        given(svc.getPrep(eq(1L))).willReturn(vo);

        mvc.perform(get("/menu/1/prep"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.items[0].ingredientName").value("番茄"))
                .andExpect(jsonPath("$.data.items[0].shared").value(true))
                .andExpect(jsonPath("$.data.items[0].status").value("READY"))
                .andExpect(jsonPath("$.data.condiments[0].ingredientName").value("食用油"))
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.readyCount").value(1));
    }

    /** PUT /menu/{id}/prep/{ingredientId}?status=READY：调用 service。 */
    @Test
    void 更新备料状态_调用service() throws Exception {
        mvc.perform(put("/menu/1/prep/5").param("status", "READY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        verify(svc).updateStatus(eq(1L), eq(5L), eq(PrepStatus.READY));
    }
}
