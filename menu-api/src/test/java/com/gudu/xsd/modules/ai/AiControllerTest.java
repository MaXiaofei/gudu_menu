package com.gudu.xsd.modules.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gudu.xsd.modules.ai.dto.DishEstimateRequest;
import com.gudu.xsd.modules.ai.dto.DishEstimateResponse;
import com.gudu.xsd.modules.ai.dto.MenuCandidate;
import com.gudu.xsd.modules.ai.dto.MenuRecommendRequest;
import com.gudu.xsd.modules.ai.dto.NutritionFillRequest;
import com.gudu.xsd.modules.ai.dto.NutritionFillResponse;
import com.gudu.xsd.modules.nutrition.IngredientNutrition;
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
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MockMvc 接口测试：mock AiService，验证 AI 两个端点的 R 结构 + source。
 * 范式照 PantryControllerTest / MealPlanControllerTest：@WebMvcTest + 排除 SaTokenConfig + mock SqlSessionFactory。
 */
@WebMvcTest(
        value = AiController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = com.gudu.xsd.config.SaTokenConfig.class))
@Import(AiControllerTest.TestSqlConfig.class)
class AiControllerTest {

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
    private AiService svc;

    private final ObjectMapper om = new ObjectMapper();

    @Test
    void 营养补全_返回R结构_且source为mock() throws Exception {
        given(svc.fillNutrition(any(NutritionFillRequest.class))).willReturn(
                new NutritionFillResponse(List.of(nut(1L, "19")), "mock"));

        String body = "{\"name\":\"番茄\",\"ingredientId\":1}";
        mvc.perform(post("/ai/nutrition/fill").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.source").value("mock"))
                .andExpect(jsonPath("$.data.nutrition[0].metricId").value(1))
                .andExpect(jsonPath("$.data.nutrition[0].value").value(19));
    }

    @Test
    void 菜单推荐_返回R结构_候选数组() throws Exception {
        MenuCandidate.DishItem item = new MenuCandidate.DishItem(1L, "番茄炒蛋", BigDecimal.ONE, new BigDecimal("10"));
        MenuCandidate c = new MenuCandidate(List.of(item), new BigDecimal("10"),
                Map.of(1L, new BigDecimal("300")), 12.5, List.of("蛋白高"), "mock");
        given(svc.recommendMenu(any(MenuRecommendRequest.class))).willReturn(List.of(c));

        String body = "{\"memberId\":1,\"budget\":50,\"scope\":\"DAY\"}";
        mvc.perform(post("/ai/menu/recommend").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].dishes[0].dishId").value(1))
                .andExpect(jsonPath("$.data[0].dishes[0].name").value("番茄炒蛋"))
                .andExpect(jsonPath("$.data[0].totalPrice").value(10))
                .andExpect(jsonPath("$.data[0].source").value("mock"))
                .andExpect(jsonPath("$.data[0].reasons[0]").value("蛋白高"));
    }

    @Test
    void 菜品估算_返回R结构_含nutrition与source() throws Exception {
        Map<Long, BigDecimal> nutrition = new java.util.LinkedHashMap<>();
        nutrition.put(1L, new BigDecimal("350"));
        nutrition.put(2L, new BigDecimal("18"));
        DishEstimateResponse resp = new DishEstimateResponse(
                "一盘番茄炒蛋", nutrition, "deepseek", "按家常份量估算");
        given(svc.estimateDish(any(DishEstimateRequest.class))).willReturn(resp);

        String body = "{\"description\":\"一盘番茄炒蛋\",\"servingFactor\":1}";
        mvc.perform(post("/ai/dish/estimate").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.source").value("deepseek"))
                .andExpect(jsonPath("$.data.description").value("一盘番茄炒蛋"))
                .andExpect(jsonPath("$.data.nutrition['1']").value(350))
                .andExpect(jsonPath("$.data.aiNote").value("按家常份量估算"));
    }

    private static IngredientNutrition nut(Long metricId, String val) {
        IngredientNutrition n = new IngredientNutrition();
        n.setMetricId(metricId);
        n.setValue(new BigDecimal(val));
        return n;
    }
}
