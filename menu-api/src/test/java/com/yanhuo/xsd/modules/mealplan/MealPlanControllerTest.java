package com.yanhuo.xsd.modules.mealplan;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yanhuo.xsd.modules.mealplan.MealPlanService.PlanDetail;
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
 * MockMvc 接口测试：mock MealPlanService，验证关键端点。
 * 不连业务库；关注 HTTP 状态码 + R 拆包 + 参数绑定。
 *
 * 主类 @MapperScan("com.yanhuo.xsd.modules.**.mapper") 在 @WebMvcTest 切片下仍会扫描全部 Mapper，
 * 每个 MapperFactoryBean 都需要一个真实（可装配的）SqlSessionFactory（mock 会在 checkDaoConfig 失败）。
 * 故用 @Import 一个测试配置：起一个 H2 内存库 + 真实 SqlSessionFactory，让 Mapper bean 全部装配。
 * 实际 DB 调用不会发生（MealPlanService 被 @MockBean 替换）。
 */
@WebMvcTest(
        value = MealPlanController.class,
        // 排除 Sa-Token 全局拦截器配置（MockMvc 切片无 SaTokenContext，会抛 InvalidContextException）
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = com.yanhuo.xsd.config.SaTokenConfig.class))
@Import(MealPlanControllerTest.TestSqlConfig.class)
class MealPlanControllerTest {

    @TestConfiguration
    static class TestSqlConfig {
        /**
         * 提供一个最小可用的 SqlSessionFactory，仅为满足 @MapperScan 注册的
         * MapperFactoryBean 的 setSqlSessionFactory/checkDaoConfig 装配。
         * DataSource 用 Mockito mock（装配期不实际取连接）；运行时由 svc mock 兜底，不触达 DB。
         */
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
    private MealPlanService svc;

    private final ObjectMapper om = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void 创建周计划_返回200_且返回planId() throws Exception {
        given(svc.createPlan(eq(LocalDate.of(2026, 6, 16)), eq("本周"))).willReturn(7L);

        String body = "{\"weekStart\":\"2026-06-16\",\"name\":\"本周\"}";
        mvc.perform(post("/mealplan")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(7));
    }

    @Test
    void 查周计划详情_返回plan和items() throws Exception {
        MealPlan plan = new MealPlan();
        plan.setId(3L);
        plan.setWeekStart(LocalDate.of(2026, 6, 16));
        plan.setName("本周");
        MealPlanItem it = new MealPlanItem();
        it.setId(11L);
        it.setPlanId(3L);
        it.setDate(LocalDate.of(2026, 6, 16));
        it.setMeal("午餐");
        it.setDishId(5L);
        it.setServingFactor(BigDecimal.ONE);
        given(svc.getPlan(3L)).willReturn(new PlanDetail(plan, List.of(it)));

        mvc.perform(get("/mealplan/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.plan.id").value(3))
                .andExpect(jsonPath("$.data.items[0].meal").value("午餐"));
    }

    @Test
    void 添加排菜项_返回重复提示() throws Exception {
        // saveItem 回灌 itemId 并返回重复项（模拟 detectDuplicates 命中）
        given(svc.saveItem(any(MealPlanItem.class))).willAnswer(inv -> {
            MealPlanItem arg = inv.getArgument(0);
            arg.setId(99L);
            return List.of(new MealPlanService.Item(5L, LocalDate.of(2026, 6, 16), "午餐"));
        });

        String body = "{\"date\":\"2026-06-16\",\"meal\":\"午餐\",\"dishId\":5,\"servingFactor\":1}";
        mvc.perform(post("/mealplan/3/item")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.itemId").value(99))
                .andExpect(jsonPath("$.data.duplicates[0].dishId").value(5));

        verify(svc).saveItem(any(MealPlanItem.class));
    }

    @Test
    void 删除排菜项_调用service() throws Exception {
        mvc.perform(delete("/mealplan/item/12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        verify(svc).deleteItem(12L);
    }

    @Test
    void 套用模板_返回插入条数() throws Exception {
        given(svc.applyTemplate(eq(2L), eq(3L))).willReturn(5);

        mvc.perform(post("/mealplan/3/apply-template").param("templateId", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(5));
    }

    @Test
    void 模板列表_返回数组() throws Exception {
        MenuTemplate t = new MenuTemplate();
        t.setId(1L);
        t.setName("标准周模板");
        given(svc.listTemplates()).willReturn(List.of(t));

        mvc.perform(get("/mealplan/templates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].name").value("标准周模板"));
    }

    @Test
    void 保存模板_返回id() throws Exception {
        given(svc.saveTemplate(any(MenuTemplate.class))).willReturn(8L);
        // snapshot 是一周菜排列快照，传一个最小合法 JSON 数组
        String body = "{\"name\":\"新模板\",\"snapshot\":[]}";
        mvc.perform(post("/mealplan/templates")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(8));
    }
}
