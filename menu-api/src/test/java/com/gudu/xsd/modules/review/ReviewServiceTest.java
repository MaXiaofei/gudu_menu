package com.gudu.xsd.modules.review;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.gudu.xsd.common.BizException;
import com.gudu.xsd.modules.dish.Dish;
import com.gudu.xsd.modules.dish.mapper.DishMapper;
import com.gudu.xsd.modules.menu.Menu;
import com.gudu.xsd.modules.menu.MenuDish;
import com.gudu.xsd.modules.menu.mapper.MenuDishMapper;
import com.gudu.xsd.modules.menu.mapper.MenuMapper;
import com.gudu.xsd.modules.review.mapper.ReviewMapper;
import com.gudu.xsd.modules.review.mapper.ReviewScoreMapper;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 点评服务测试（V43：食集评价）。
 * 纯函数（averageStar/averageByDimension）不碰 Mapper；submit/menuOverview/mine 用 mock Mapper + mockStatic StpUtil。
 */
class ReviewServiceTest {

    private final ReviewMapper reviewMapper = mock(ReviewMapper.class);
    private final ReviewScoreMapper reviewScoreMapper = mock(ReviewScoreMapper.class);
    private final MenuMapper menuMapper = mock(MenuMapper.class);
    private final MenuDishMapper menuDishMapper = mock(MenuDishMapper.class);
    private final DishMapper dishMapper = mock(DishMapper.class);

    private final ReviewService svc =
            new ReviewService(reviewMapper, reviewScoreMapper, menuMapper, menuDishMapper, dishMapper);

    record ScoreRow(Long dimensionId, Integer score) implements ReviewService.DimensionScore {}

    // ===================== 纯函数（保留） =====================

    @Test
    void 菜品总评均分_按星级求平均() {
        assertThat(svc.averageStar(List.of(5, 4, 3))).isEqualByComparingTo("4.0");
    }

    @Test
    void 空列表返回0() {
        assertThat(svc.averageStar(List.of())).isEqualByComparingTo("0.0");
    }

    @Test
    void 单元素原样返回() {
        assertThat(svc.averageStar(List.of(5))).isEqualByComparingTo("5.0");
    }

    @Test
    void 各维度均分_按维度分组求平均() {
        var scores = List.of(
            new ScoreRow(1L, 4), new ScoreRow(2L, 3),
            new ScoreRow(1L, 5), new ScoreRow(2L, 4));
        Map<Long, String> avg = svc.averageByDimension(scores);
        assertThat(avg.get(1L)).isEqualTo("4.5");
        assertThat(avg.get(2L)).isEqualTo("3.5");
    }

    // ===================== submit（菜品/食集二选一） =====================

    @Test
    void submit_菜品和食集都空_抛异常() {
        ReviewSaveDTO dto = new ReviewSaveDTO();
        dto.setStarRating(5);
        assertThatThrownBy(() -> svc.submit(dto))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("二选一");
    }

    @Test
    void submit_菜品和食集都有_抛异常() {
        ReviewSaveDTO dto = new ReviewSaveDTO();
        dto.setDishId(10L);
        dto.setMenuId(7L);
        dto.setStarRating(5);
        assertThatThrownBy(() -> svc.submit(dto))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("二选一");
    }

    @Test
    void submit_食集评价_存menuId不存dishId() {
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            SaSession session = mock(SaSession.class);
            stp.when(StpUtil::getSession).thenReturn(session);
            when(session.getLong("currentMemberId")).thenReturn(1L);
            when(reviewMapper.insert(any())).thenAnswer(inv -> {
                ((Review) inv.getArgument(0)).setId(9L);
                return 1;
            });
            ReviewSaveDTO dto = new ReviewSaveDTO();
            dto.setMenuId(7L);
            dto.setStarRating(4);
            dto.setDimensionScores(Map.of(1L, 4, 2L, 3));

            Long id = svc.submit(dto);

            assertThat(id).isEqualTo(9L);
            verify(reviewMapper).insert(any());
            verify(reviewScoreMapper, org.mockito.Mockito.times(2)).insert(any());
        }
    }

    // ===================== menuOverview（统一评价页数据） =====================

    @Test
    void menuOverview_返回食集信息_未登录不标已评() {
        Menu menu = new Menu();
        menu.setId(7L);
        menu.setName("今晚的饭");
        menu.setFinishedAt(LocalDateTime.of(2026, 7, 2, 19, 20));
        when(menuMapper.selectById(7L)).thenReturn(menu);
        MenuDish md = new MenuDish();
        md.setMenuId(7L);
        md.setDishId(10L);
        when(menuDishMapper.selectList(any())).thenReturn(List.of(md));
        Dish tomato = new Dish();
        tomato.setId(10L);
        tomato.setName("番茄炒蛋");
        when(dishMapper.selectBatchIds(List.of(10L))).thenReturn(List.of(tomato));

        MenuReviewOverviewVO vo = svc.menuOverview(7L);

        assertThat(vo.menuName()).isEqualTo("今晚的饭");
        assertThat(vo.dishCount()).isEqualTo(1);
        assertThat(vo.dishes()).hasSize(1);
        assertThat(vo.dishes().get(0).dishName()).isEqualTo("番茄炒蛋");
        assertThat(vo.dishes().get(0).starRating()).isNull(); // 未登录：不标已评
        assertThat(vo.menuReview()).isNull();
    }

    @Test
    void menuOverview_已登录_返回我的已评星级() {
        Menu menu = new Menu();
        menu.setId(7L);
        menu.setName("今晚的饭");
        when(menuMapper.selectById(7L)).thenReturn(menu);
        MenuDish md = new MenuDish();
        md.setMenuId(7L);
        md.setDishId(10L);
        when(menuDishMapper.selectList(any())).thenReturn(List.of(md));
        Dish tomato = new Dish();
        tomato.setId(10L);
        tomato.setName("番茄炒蛋");
        when(dishMapper.selectBatchIds(List.of(10L))).thenReturn(List.of(tomato));

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            SaSession session = mock(SaSession.class);
            stp.when(StpUtil::getSession).thenReturn(session);
            when(session.getLong("currentMemberId")).thenReturn(1L);
            // 第一次 selectList = 食集整体评价（我的最近一条）；第二次 = 菜品评价（我的最近一条）
            Review menuReview = new Review();
            menuReview.setId(3L);
            menuReview.setStarRating(5);
            menuReview.setCreateTime(LocalDateTime.now());
            Review dishReview = new Review();
            dishReview.setDishId(10L);
            dishReview.setStarRating(4);
            when(reviewMapper.selectList(any())).thenReturn(List.of(menuReview), List.of(dishReview));
            // 食集评价维度分
            ReviewScore score = new ReviewScore();
            score.setDimensionId(1L);
            score.setScore(4);
            when(reviewScoreMapper.selectList(any())).thenReturn(List.of(score));

            MenuReviewOverviewVO vo = svc.menuOverview(7L);

            assertThat(vo.menuReview().reviewed()).isTrue();
            assertThat(vo.menuReview().starRating()).isEqualTo(5);
            assertThat(vo.menuReview().dimensionScores()).containsEntry(1L, 4);
            assertThat(vo.dishes().get(0).starRating()).isEqualTo(4);
        }
    }

    @Test
    void menuOverview_食集不存在_抛异常() {
        when(menuMapper.selectById(9L)).thenReturn(null);
        assertThatThrownBy(() -> svc.menuOverview(9L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("食集不存在");
    }

    // ===================== mine（我的评价） =====================

    @Test
    void mine_未登录_返回空() {
        MyReviewsVO vo = svc.mine();
        assertThat(vo.reviews()).isEmpty();
        assertThat(vo.pendingMenus()).isEmpty();
    }

    // ===================== listByMenu（V43 食集整体评价列表） =====================

    @Test
    void listByMenu_返回该食集评价列表() {
        Review r1 = new Review();
        r1.setId(1L);
        r1.setMenuId(7L);
        r1.setStarRating(5);
        Review r2 = new Review();
        r2.setId(2L);
        r2.setMenuId(7L);
        r2.setStarRating(4);
        when(reviewMapper.selectList(any())).thenReturn(List.of(r1, r2));

        List<Review> list = svc.listByMenu(7L);

        assertThat(list).hasSize(2);
        assertThat(list).extracting(Review::getStarRating).containsExactly(5, 4);
    }

    @Test
    void listByMenu_无评价_返回空列表() {
        when(reviewMapper.selectList(any())).thenReturn(List.of());

        assertThat(svc.listByMenu(7L)).isEmpty();
    }

    // ===================== mine（已登录路径） =====================

    @Test
    void mine_已登录_返回评价历史和待评价食集() {
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            SaSession session = mock(SaSession.class);
            stp.when(StpUtil::getSession).thenReturn(session);
            when(session.getLong("currentMemberId")).thenReturn(1L);

            // 评价历史：1 条菜品评价 + 1 条食集评价
            Review dishReview = new Review();
            dishReview.setId(1L);
            dishReview.setDishId(10L);
            dishReview.setStarRating(5);
            dishReview.setCreateTime(LocalDateTime.now());
            Review menuReview = new Review();
            menuReview.setId(2L);
            menuReview.setMenuId(7L);
            menuReview.setStarRating(4);
            menuReview.setCreateTime(LocalDateTime.now());
            // 第 1 次 selectList = 评价历史；后续为 mine 内部的待评价查询
            when(reviewMapper.selectList(any())).thenReturn(
                    List.of(dishReview, menuReview), // 历史
                    List.of(),                        // 已评价食集
                    List.of());                        // 已评价菜品
            Dish d = new Dish();
            d.setId(10L);
            d.setName("番茄炒蛋");
            when(dishMapper.selectBatchIds(any())).thenReturn(List.of(d));
            Menu m = new Menu();
            m.setId(7L);
            m.setName("今晚的饭");
            when(menuMapper.selectBatchIds(any())).thenReturn(List.of(m));
            // 待评价食集：1 个 DONE 食集未评价
            Menu doneMenu = new Menu();
            doneMenu.setId(9L);
            doneMenu.setName("周末聚餐");
            doneMenu.setStatus("DONE");
            when(menuMapper.selectList(any())).thenReturn(List.of(doneMenu));
            when(menuDishMapper.selectList(any())).thenReturn(List.of()); // 无菜品 → 全未评

            MyReviewsVO vo = svc.mine();

            // 评价历史：菜品名 + 食集名都回填
            assertThat(vo.reviews()).hasSize(2);
            assertThat(vo.reviews()).extracting(MyReviewsVO.ReviewEntry::name)
                    .containsExactlyInAnyOrder("番茄炒蛋", "今晚的饭");
            // 待评价：周末聚餐 DONE 且无评价 → 进待评
            assertThat(vo.pendingMenus()).hasSize(1);
            assertThat(vo.pendingMenus().get(0).menuName()).isEqualTo("周末聚餐");
        }
    }

    @Test
    void mine_已登录_无任何数据_返回空() {
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            SaSession session = mock(SaSession.class);
            stp.when(StpUtil::getSession).thenReturn(session);
            when(session.getLong("currentMemberId")).thenReturn(1L);
            when(reviewMapper.selectList(any())).thenReturn(List.of());
            when(menuMapper.selectList(any())).thenReturn(List.of());

            MyReviewsVO vo = svc.mine();

            assertThat(vo.reviews()).isEmpty();
            assertThat(vo.pendingMenus()).isEmpty();
        }
    }
}
