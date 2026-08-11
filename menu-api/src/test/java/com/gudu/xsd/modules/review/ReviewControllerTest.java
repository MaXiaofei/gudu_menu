package com.gudu.xsd.modules.review;

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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ReviewController MockMvc 测试（V43：菜品 + 食集评价）。
 * 范式照 FoodLogControllerTest：@WebMvcTest + 排除 SaTokenConfig + mock SqlSessionFactory 装配 Mapper bean。
 */
@WebMvcTest(
        value = ReviewController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = com.gudu.xsd.config.SaTokenConfig.class))
@Import(ReviewControllerTest.TestSqlConfig.class)
class ReviewControllerTest {

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
    ReviewService svc;

    /** POST /review 提交菜品评价 → 返回评价 id。 */
    @Test
    void POST_提交菜品评价_返回id() throws Exception {
        given(svc.submit(any())).willReturn(9L);

        mvc.perform(post("/review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dishId\":10,\"starRating\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(9));
    }

    /** POST /review 缺 starRating（@NotNull）→ 校验失败返回 code=1。 */
    @Test
    void POST_缺星级_校验失败() throws Exception {
        mvc.perform(post("/review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dishId\":10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));
    }

    /** GET /review/dish/{dishId} → 菜品评价列表。 */
    @Test
    void GET_某菜点评列表() throws Exception {
        Review r = new Review();
        r.setId(1L);
        r.setDishId(10L);
        r.setStarRating(5);
        r.setText("好吃");
        given(svc.listByDish(eq(10L))).willReturn(List.of(r));

        mvc.perform(get("/review/dish/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].starRating").value(5))
                .andExpect(jsonPath("$.data[0].text").value("好吃"));
    }

    /** GET /review/menu/{menuId} → 食集整体评价列表（V43）。 */
    @Test
    void GET_某食集整体评价列表() throws Exception {
        Review r = new Review();
        r.setId(2L);
        r.setMenuId(7L);
        r.setStarRating(4);
        given(svc.listByMenu(eq(7L))).willReturn(List.of(r));

        mvc.perform(get("/review/menu/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].starRating").value(4));
        verify(svc).listByMenu(7L);
    }

    /** GET /review/dish/{dishId}/avg → 均分（由 listByDish + averageStar 计算）。 */
    @Test
    void GET_某菜均分() throws Exception {
        Review r1 = new Review();
        r1.setStarRating(4);
        Review r2 = new Review();
        r2.setStarRating(5);
        given(svc.listByDish(eq(10L))).willReturn(List.of(r1, r2));
        // averageStar([4,5]) = 4.5
        given(svc.averageStar(any())).willReturn(new BigDecimal("4.5"));

        mvc.perform(get("/review/dish/10/avg"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.star").value("4.5"))
                .andExpect(jsonPath("$.data.count").value(2));
    }

    /** GET /review/menu-overview/{menuId} → 统一评价页数据（V43）。 */
    @Test
    void GET_统一评价页() throws Exception {
        MenuReviewOverviewVO vo = new MenuReviewOverviewVO(
                7L, "今晚的饭", LocalDateTime.of(2026, 7, 2, 19, 20), 2,
                new MenuReviewOverviewVO.MenuReviewStatus(true, 5, Map.of(1L, 4), LocalDateTime.now()),
                List.of(new MenuReviewOverviewVO.DishReviewStatus(10L, "番茄炒蛋", null, 4)));
        given(svc.menuOverview(eq(7L))).willReturn(vo);

        mvc.perform(get("/review/menu-overview/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.menuName").value("今晚的饭"))
                .andExpect(jsonPath("$.data.dishCount").value(2))
                .andExpect(jsonPath("$.data.menuReview.reviewed").value(true))
                .andExpect(jsonPath("$.data.menuReview.starRating").value(5))
                .andExpect(jsonPath("$.data.dishes[0].dishName").value("番茄炒蛋"));
    }

    /** GET /review/mine → 我的评价（历史 + 待评食集，V43）。 */
    @Test
    void GET_我的评价() throws Exception {
        MyReviewsVO vo = new MyReviewsVO(
                List.of(new MyReviewsVO.ReviewEntry(1L, 10L, null, "番茄炒蛋", 5, LocalDateTime.now())),
                List.of(new MyReviewsVO.PendingMenu(7L, "周末聚餐", LocalDateTime.now(), 3, 1, false)));
        given(svc.mine()).willReturn(vo);

        mvc.perform(get("/review/mine"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviews[0].name").value("番茄炒蛋"))
                .andExpect(jsonPath("$.data.pendingMenus[0].menuName").value("周末聚餐"))
                .andExpect(jsonPath("$.data.pendingMenus[0].dishCount").value(3));
    }
}
