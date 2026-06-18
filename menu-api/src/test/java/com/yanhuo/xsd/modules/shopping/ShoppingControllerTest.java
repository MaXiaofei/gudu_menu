package com.yanhuo.xsd.modules.shopping;

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
 * MockMvc 接口测试：mock ShoppingService，验证关键端点（redesign 后）。
 * 范式照 PantryControllerTest：@WebMvcTest + 排除 SaTokenConfig + mock SqlSessionFactory 装配 Mapper bean。
 *
 * <p>generate 三 sourceType（menu/dish/plan）；updatePurchase 用户填采购量+单位；list VO 含采购单位中文。
 */
@WebMvcTest(
        value = ShoppingController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = com.yanhuo.xsd.config.SaTokenConfig.class))
@Import(ShoppingControllerTest.TestSqlConfig.class)
class ShoppingControllerTest {

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
    private ShoppingService svc;

    private final ObjectMapper om = new ObjectMapper();

    /** 造一个明细 VO（带中文 + 参考克 + 采购单位名）。 */
    private ShoppingItemVO itemVO(Long id, String ing, BigDecimal refG, BigDecimal amt, Long unitId, String unitName) {
        ShoppingItemVO v = new ShoppingItemVO();
        v.setId(id);
        v.setListId(1L);
        v.setIngredientId(10L);
        v.setIngredientName(ing);
        v.setReferenceGrams(refG);
        v.setPurchaseAmount(amt);
        v.setPurchaseUnitId(unitId);
        v.setPurchaseUnitName(unitName);
        v.setPurchaseCategoryId(24L);
        v.setPurchaseCategoryName("蔬菜");
        v.setPurchased(0);
        return v;
    }

    @Test
    void 从菜单生成_返回listId() throws Exception {
        given(svc.generate(eq("menu"), eq(3L), any())).willReturn(99L);
        String body = om.writeValueAsString(Map.of("sourceType", "menu", "sourceId", 3));

        mvc.perform(post("/shopping/generate").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(99));
        verify(svc).generate(eq("menu"), eq(3L), any());
    }

    @Test
    void 从菜品多选生成_返回listId() throws Exception {
        given(svc.generate(eq("dish"), eq(null), any())).willReturn(88L);
        String body = om.writeValueAsString(Map.of("sourceType", "dish", "sourceIds", List.of(11, 12)));

        mvc.perform(post("/shopping/generate").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(88));
        verify(svc).generate(eq("dish"), eq(null), any());
    }

    @Test
    void 从周计划生成_返回listId() throws Exception {
        given(svc.generate(eq("plan"), eq(7L), any())).willReturn(77L);
        String body = om.writeValueAsString(Map.of("sourceType", "plan", "sourceId", 7));

        mvc.perform(post("/shopping/generate").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(77));
        verify(svc).generate(eq("plan"), eq(7L), any());
    }

    @Test
    void 查采购清单详情_返回带参考克和采购单位中文() throws Exception {
        ShoppingListVO vo = new ShoppingListVO();
        vo.setId(1L);
        vo.setSourcePlanId(7L);
        vo.setTimeRange("plan");
        vo.setStartDate(LocalDate.of(2026, 6, 16));
        vo.setEndDate(LocalDate.of(2026, 6, 22));
        // 番茄：参考约 500g，用户填 1 斤
        ShoppingItemVO tomato = itemVO(1L, "番茄", new BigDecimal("500"), new BigDecimal("1"), 40L, "斤");
        vo.setItems(List.of(tomato));
        vo.setGrouped(Map.of(24L, List.of(tomato)));
        vo.setCategoryNames(Map.of(24L, "蔬菜"));
        given(svc.getDetail(eq(1L))).willReturn(vo);

        mvc.perform(get("/shopping/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.items[0].ingredientName").value("番茄"))
                .andExpect(jsonPath("$.data.items[0].referenceGrams").value(500))
                .andExpect(jsonPath("$.data.items[0].purchaseAmount").value(1))
                .andExpect(jsonPath("$.data.items[0].purchaseUnitName").value("斤"))
                .andExpect(jsonPath("$.data.items[0].purchaseCategoryName").value("蔬菜"));
    }

    @Test
    void 用户填采购量和单位_调用updatePurchase() throws Exception {
        String body = om.writeValueAsString(Map.of(
                "purchaseAmount", 2, "purchaseUnitId", 40));

        mvc.perform(put("/shopping/item/5").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        verify(svc).updatePurchase(eq(5L), eq(new BigDecimal("2")), eq(40L));
    }

    @Test
    void 勾选已买_调用service() throws Exception {
        mvc.perform(put("/shopping/item/5/purchased"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        verify(svc).togglePurchased(5L);
    }

    @Test
    void 删除明细_调用service() throws Exception {
        mvc.perform(delete("/shopping/item/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        verify(svc).deleteItem(5L);
    }

    @Test
    void 删除整张清单_调用service() throws Exception {
        mvc.perform(delete("/shopping/8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        verify(svc).deleteList(8L);
    }
}
