package com.yanhuo.xsd.modules.pantry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yanhuo.xsd.common.PageQuery;
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
                classes = com.yanhuo.xsd.config.SaTokenConfig.class))
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
        v.setUnitName("g");
        v.setExpireDate(expire);
        v.setLowThreshold(new BigDecimal("10"));
        return v;
    }

    @Test
    void 库存分页列表_返回200_且返回VO数组() throws Exception {
        Page<PantryVO> page = new Page<>(1, 10, 1);
        page.setRecords(List.of(vo(1L, "鸡蛋", new BigDecimal("12"), LocalDate.of(2026, 6, 22))));
        // 用 doReturn().when() 形式绕开重载歧义（svc.page 同时匹配 IService<E>.page(E) 与 page(PageQuery)）
        org.mockito.Mockito.doReturn(page).when(svc).page(org.mockito.ArgumentMatchers.any(PageQuery.class));

        mvc.perform(get("/pantry").param("pageNum", "1").param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].ingredientName").value("鸡蛋"))
                .andExpect(jsonPath("$.data.records[0].amount").value(12));
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
    void 不足查询_返回VO数组() throws Exception {
        given(svc.listLow()).willReturn(List.of(
                vo(2L, "面粉", new BigDecimal("3"), null)));

        mvc.perform(get("/pantry/low"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].ingredientName").value("面粉"));
    }

    @Test
    void 新增库存_返回id() throws Exception {
        given(svc.save(any(Pantry.class))).willAnswer(inv -> {
            ((Pantry) inv.getArgument(0)).setId(77L);
            return true;
        });

        String body = "{\"ingredientId\":10,\"amount\":12,\"unitId\":20,\"expireDate\":\"2026-06-22\",\"lowThreshold\":10}";
        mvc.perform(post("/pantry").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(77));
    }

    @Test
    void 更新库存_调用service() throws Exception {
        String body = "{\"id\":1,\"ingredientId\":10,\"amount\":8}";
        mvc.perform(put("/pantry").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        verify(svc).updateById(any(Pantry.class));
    }

    @Test
    void 删除库存_调用service() throws Exception {
        mvc.perform(delete("/pantry/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        verify(svc).removeById(5L);
    }
}
