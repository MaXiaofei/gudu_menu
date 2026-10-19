package com.gudu.xsd.modules.pantry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gudu.xsd.common.PageQuery;
import com.gudu.xsd.modules.pantry.application.StockAppService;
import com.gudu.xsd.modules.pantry.application.dto.BestBeforeUpdate;
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
import static org.mockito.Mockito.never;
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

    @MockBean
    private StockAppService stockAppService;

    @Test
    void 设档位_不传bestBefore_保质期不改() throws Exception {
        mvc.perform(put("/pantry/10/level").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"level\":\"LOW\",\"note\":\"改\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        verify(stockAppService).adjustLevel(10L, "LOW", BestBeforeUpdate.keep(), "改");
    }

    @Test
    void 设档位_传bestBefore_设置保质期() throws Exception {
        mvc.perform(put("/pantry/10/level").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"level\":\"ENOUGH\",\"bestBefore\":\"2026-10-20\"}"))
                .andExpect(status().isOk());
        verify(stockAppService).adjustLevel(10L, "ENOUGH", BestBeforeUpdate.set(LocalDate.of(2026, 10, 20)), null);
    }

    @Test
    void 设档位_bestBefore传null_清除保质期() throws Exception {
        mvc.perform(put("/pantry/10/level").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"level\":\"ENOUGH\",\"bestBefore\":null}"))
                .andExpect(status().isOk());
        verify(stockAppService).adjustLevel(10L, "ENOUGH", BestBeforeUpdate.set(null), null);
    }

    @Test
    void 手动入库_带bestBefore_入库后补登保质期() throws Exception {
        given(svc.manualAdd(eq(10L), any(), eq("ENOUGH"), any())).willReturn(10L);
        mvc.perform(post("/pantry/manual").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ingredientId\":10,\"level\":\"ENOUGH\",\"bestBefore\":\"2026-10-18\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        verify(stockAppService).changeBestBefore(10L, LocalDate.of(2026, 10, 18));
    }

    @Test
    void 手动入库_不带bestBefore_不改保质期() throws Exception {
        given(svc.manualAdd(eq(10L), any(), any(), any())).willReturn(10L);
        mvc.perform(post("/pantry/manual").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ingredientId\":10}"))
                .andExpect(status().isOk());
        verify(stockAppService, never()).changeBestBefore(any(), any());
    }

    @Test
    void 分组列表_返回保质期与剩余天数() throws Exception {
        PantryGroupedVO vo = new PantryGroupedVO();
        vo.setSummary(new PantryGroupedVO.Summary());
        PantryGroupedVO.Item it = new PantryGroupedVO.Item();
        it.setIngredientId(10L);
        it.setIngredientName("牛奶");
        it.setLevel("ENOUGH");
        it.setBestBefore(LocalDate.of(2026, 10, 18));
        it.setDaysLeft(2);
        vo.setItems(List.of(it));
        given(svc.grouped(any(), any(), any(), any())).willReturn(vo);

        mvc.perform(get("/pantry/grouped"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].bestBefore").value("2026-10-18"))
                .andExpect(jsonPath("$.data.items[0].daysLeft").value(2));
    }

    @Test
    void 删除档位_调用removeLevel() throws Exception {
        mvc.perform(delete("/pantry/10/level"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        verify(svc).removeLevel(10L, StockLog.ACTION_MANUAL, null, null);
    }
}
