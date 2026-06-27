package com.gudu.xsd.modules.dailylog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MockMvc 接口测试：mock DailyLogService，验证 submit / list / nutrition。
 * 范式照 PantryControllerTest：@WebMvcTest + 排除 SaTokenConfig + mock SqlSessionFactory 装配 Mapper bean。
 */
@WebMvcTest(
        value = DailyLogController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = com.gudu.xsd.config.SaTokenConfig.class))
@Import(DailyLogControllerTest.TestSqlConfig.class)
class DailyLogControllerTest {

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
    private DailyLogService svc;

    private final ObjectMapper om = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void 提交当日日志_返回logId() throws Exception {
        given(svc.submit(any(DailyLogSaveDTO.class))).willReturn(88L);

        String body = "{\"date\":\"2026-06-19\",\"note\":\"今天吃了零食\","
                + "\"items\":[{\"ingredientId\":10,\"amount\":200},"
                + "{\"dishId\":3,\"amount\":2,\"servingFactor\":1}]}";
        mvc.perform(post("/dailylog").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(88));
        verify(svc).submit(any(DailyLogSaveDTO.class));
    }

    @Test
    void 查当日日志_返回VO() throws Exception {
        DailyLogVO vo = new DailyLogVO();
        vo.setId(1L);
        vo.setMemberId(7L);
        vo.setDate(LocalDate.of(2026, 6, 19));
        vo.setNote("早餐");
        DailyLogItem item = new DailyLogItem();
        item.setId(11L);
        item.setLogId(1L);
        item.setIngredientId(10L);
        item.setAmount(new BigDecimal("200"));
        vo.setItems(List.of(item));

        given(svc.currentMemberId()).willReturn(7L);
        given(svc.listByDate(eq(7L), eq(LocalDate.of(2026, 6, 19)))).willReturn(vo);

        mvc.perform(get("/dailylog").param("date", "2026-06-19"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.note").value("早餐"))
                .andExpect(jsonPath("$.data.items[0].amount").value(200));
    }

    @Test
    void 营养汇总_返回metricIdMap() throws Exception {
        given(svc.nutritionSummary(eq(1L)))
                .willReturn(Map.of(1L, new BigDecimal("444"), 2L, new BigDecimal("33")));

        mvc.perform(get("/dailylog/1/nutrition"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.1").value(444))
                .andExpect(jsonPath("$.data.2").value(33));
    }
}
