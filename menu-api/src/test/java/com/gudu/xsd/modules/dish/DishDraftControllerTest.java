package com.gudu.xsd.modules.dish;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * DishDraftController MockMvc 测试（§16.4 写菜谱草稿）。
 * 范式照 FoodLogControllerTest：@WebMvcTest + 排除 SaTokenConfig + mock SqlSessionFactory 装配 Mapper bean。
 */
@WebMvcTest(
        value = DishDraftController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = com.gudu.xsd.config.SaTokenConfig.class))
@Import(DishDraftControllerTest.TestSqlConfig.class)
class DishDraftControllerTest {

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

    @Autowired
    MockMvc mvc;
    @MockBean
    DishDraftService svc;

    /** POST /dish/draft 新建草稿 → 返回草稿 id。 */
    @Test
    void POST_保存草稿_返回id() throws Exception {
        given(svc.save(any())).willReturn(5L);

        mvc.perform(post("/dish/draft")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"新菜\",\"steps\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(5));
    }

    /** GET /dish/draft/list → 本人草稿箱分页。 */
    @Test
    void GET_草稿箱列表_分页() throws Exception {
        DishDraftDTO.ListItem item = new DishDraftDTO.ListItem();
        item.setId(5L);
        item.setName("新菜");
        item.setIngredientCount(2);
        item.setStepCount(3);
        item.setUpdateTime(LocalDateTime.of(2026, 8, 10, 12, 0));
        Page<DishDraftDTO.ListItem> page = new Page<>(1, 10);
        page.setTotal(1);
        page.setRecords(List.of(item));
        given(svc.list(eq(1L), eq(10L))).willReturn(page);

        mvc.perform(get("/dish/draft/list").param("pageNum", "1").param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].name").value("新菜"))
                .andExpect(jsonPath("$.data.records[0].ingredientCount").value(2));
        verify(svc).list(1L, 10L);
    }

    /** GET /dish/draft/{id} → 草稿详情（恢复编辑）。 */
    @Test
    void GET_草稿详情() throws Exception {
        DishDraftDTO.Detail detail = new DishDraftDTO.Detail();
        detail.setId(5L);
        detail.setName("新菜");
        detail.setTagIds(List.of(31L));
        detail.setCuisineIds(List.of(41L));
        DishDraftDTO.DraftIngredient ing = new DishDraftDTO.DraftIngredient();
        ing.setIngredientId(10L);
        ing.setIngredientName("番茄");
        ing.setAmountText("2 个");
        detail.setIngredients(List.of(ing));
        DishStep step = new DishStep();
        step.setSeq(1);
        step.setText("切块");
        detail.setSteps(List.of(step));
        given(svc.detail(eq(5L))).willReturn(detail);

        mvc.perform(get("/dish/draft/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("新菜"))
                .andExpect(jsonPath("$.data.tagIds[0]").value(31))
                .andExpect(jsonPath("$.data.ingredients[0].amountText").value("2 个"))
                .andExpect(jsonPath("$.data.steps[0].text").value("切块"));
    }

    /** DELETE /dish/draft/{id} → 删除草稿。 */
    @Test
    void DELETE_删除草稿() throws Exception {
        mvc.perform(delete("/dish/draft/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        verify(svc).delete(5L);
    }
}
