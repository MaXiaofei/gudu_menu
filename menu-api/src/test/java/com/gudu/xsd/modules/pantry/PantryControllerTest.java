package com.gudu.xsd.modules.pantry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gudu.xsd.common.PageQuery;
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
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MockMvc 接口测试：mock PantryService，验证关键端点。
 * 范式照 MealPlanControllerTest：@WebMvcTest + 排除 SaTokenConfig + H2/mock SqlSessionFactory 装配 Mapper bean。
 */
@WebMvcTest(
        value = PantryController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = com.gudu.xsd.config.SaTokenConfig.class))
@Import(PantryControllerTest.TestSqlConfig.class)
class PantryControllerTest {

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
    private PantryService svc;

    private final ObjectMapper om = new ObjectMapper().registerModule(new JavaTimeModule());

    private PantryVO vo(Long id, String name, BigDecimal amount, LocalDate expire) {
        PantryVO v = new PantryVO();
        v.setId(id);
        v.setIngredientId(10L);
        v.setIngredientName(name);
        v.setAmount(amount);
        // V55：unitName 随食材去单位删除
        v.setExpireDate(expire);
        // lowThreshold 已挪到 ingredient（V39），pantry 行不再带阈值
        return v;
    }

    /** 分组项（listLow 现在返回 PantryGroupedVO.Item，V42 档位版）。 */
    private PantryGroupedVO.Item lowItem(String name) {
        PantryGroupedVO.Item it = new PantryGroupedVO.Item();
        it.setIngredientId(10L);
        it.setIngredientName(name);
        it.setLevel("LOW");
        return it;
    }

    @Test
    void 临期查询_返回VO数组() throws Exception {
        given(svc.listExpiring(eq(3))).willReturn(List.of(
                vo(1L, "牛奶", new BigDecimal("500"), LocalDate.of(2026, 6, 22))));

        mvc.perform(get("/pantry/expiring").param("days", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].ingredientName").value("牛奶"));
    }

    @Test
    void 删除档位_调用removeLevel() throws Exception {
        mvc.perform(delete("/pantry/10/level"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        verify(svc).removeLevel(10L, StockLog.ACTION_MANUAL, null, null);
    }
}
