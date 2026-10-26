package com.gudu.xsd.modules.dish;

import com.gudu.xsd.common.BizException;
import com.gudu.xsd.common.tenant.TenantContext;
import com.gudu.xsd.common.tenant.TenantContextHolder;
import com.gudu.xsd.modules.cookbook.CookbookService;
import com.gudu.xsd.modules.cookbook.application.CookingRecordAppService;
import com.gudu.xsd.modules.cookbook.mapper.FavoriteMapper;
import com.gudu.xsd.modules.dish.mapper.DishMapper;
import com.gudu.xsd.modules.mealplan.application.MealPlanAppService;
import com.gudu.xsd.modules.menu.MenuDish;
import com.gudu.xsd.modules.menu.MenuSaveDTO;
import com.gudu.xsd.modules.menu.MenuService;
import com.gudu.xsd.modules.menu.application.CookAppService;
import com.gudu.xsd.modules.menu.CookService;
import com.gudu.xsd.modules.review.ReviewSaveDTO;
import com.gudu.xsd.modules.review.ReviewService;
import com.gudu.xsd.modules.review.mapper.ReviewMapper;
import com.gudu.xsd.modules.shopping.ShoppingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

/**
 * 菜谱入口可见性（multi-tenant-family E.1，打回项 1）：每个带 dishId 入参的入口，
 * 对别家私有菜谱（id={@value #FOREIGN}）读 / 写一律 404 且不落库；对公共菜谱（id={@value #PUBLIC}）可读可引用，
 * 但改 / 删菜谱本身需平台运营（403）。
 *
 * <p>SQL 层（dish 及子表被插件改写）见 config.TenantInterceptorSqlTest；这里用 DishMapper 桩模拟插件结果：
 * 别家私有菜谱 selectById / selectBatchIds 查不到。
 */
class DishEntryVisibilityTest {

    static final long PUBLIC = 1L;
    static final long FOREIGN = 99L;

    final DishMapper dishMapper = mock(DishMapper.class);
    final DishVisibilityGuard guard = new DishVisibilityGuard(dishMapper);

    @BeforeEach
    void setUp() {
        TenantContextHolder.set(new TenantContext(7L, 70L, 700L, TenantContext.MEMBER));
        Dish pub = new Dish();
        pub.setId(PUBLIC);
        pub.setName("番茄炒蛋");
        when(dishMapper.selectById(PUBLIC)).thenReturn(pub);
        when(dishMapper.selectById(FOREIGN)).thenReturn(null);
        when(dishMapper.selectBatchIds(anyCollection())).thenAnswer(i -> ((java.util.Collection<?>) i.getArgument(0))
                .stream().filter(id -> id.equals(PUBLIC)).map(id -> pub).toList());
    }

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    static void assert404(Runnable r) {
        assertThatThrownBy(r::run).isInstanceOfSatisfying(BizException.class,
                e -> assertThat(e.getCode()).isEqualTo(404));
    }

    // ------------------------------------------------------------------ 守卫本身

    @Test
    void 守卫_公共可见_别家私有404_批量任一不可见404() {
        assertThat(guard.requireVisible(PUBLIC).getId()).isEqualTo(PUBLIC);
        assert404(() -> guard.requireVisible(FOREIGN));
        assert404(() -> guard.requireVisible(null));
        assert404(() -> guard.requireAllVisible(List.of(PUBLIC, FOREIGN)));
        guard.requireAllVisible(List.of(PUBLIC, PUBLIC));
    }

    // ------------------------------------------------------------------ 收藏

    @Test
    void 收藏_取消收藏() {
        FavoriteMapper fav = mock(FavoriteMapper.class);
        when(fav.selectCount(any())).thenReturn(0L);
        CookbookService svc = new CookbookService(fav, dishMapper, null);
        svc.setDishGuard(guard);
        assert404(() -> svc.favorite(700L, FOREIGN));
        assert404(() -> svc.unfavorite(700L, FOREIGN));
        verify(fav, never()).insert(any(com.gudu.xsd.modules.cookbook.Favorite.class));
        verify(fav, never()).delete(any());
        svc.favorite(700L, PUBLIC);
        verify(fav).insert(any(com.gudu.xsd.modules.cookbook.Favorite.class));
    }

    // ------------------------------------------------------------------ 点评

    @Test
    void 点评_写菜评价_读菜评价与均分() {
        ReviewMapper reviews = mock(ReviewMapper.class);
        ReviewService svc = new ReviewService(reviews, mock(com.gudu.xsd.modules.review.mapper.ReviewScoreMapper.class),
                null, null, dishMapper);
        svc.setDishGuard(guard);
        ReviewSaveDTO dto = new ReviewSaveDTO();
        dto.setDishId(FOREIGN);
        dto.setStarRating(5);
        assert404(() -> svc.submit(dto));
        assert404(() -> svc.listByDish(FOREIGN));
        verify(reviews, never()).insert(any(com.gudu.xsd.modules.review.Review.class));
        dto.setDishId(PUBLIC);
        assertThat(svc.submit(dto)).isNull(); // mapper 桩不回填 id；未抛异常即放行
        svc.listByDish(PUBLIC);
    }

    // ------------------------------------------------------------------ 食集加菜（含 Agent AddToMenuTool → addDishes）

    @Test
    void 食集_新建或整单保存_追加菜() {
        com.gudu.xsd.modules.menu.mapper.MenuDishMapper menuDish = mock(com.gudu.xsd.modules.menu.mapper.MenuDishMapper.class);
        MenuService svc = spy(new MenuService(menuDish, dishMapper, null, null, null));
        svc.setDishGuard(guard);
        doReturn(new com.gudu.xsd.modules.menu.Menu()).when(svc).getById(5L);
        MenuSaveDTO dto = new MenuSaveDTO();
        com.gudu.xsd.modules.menu.Menu m = new com.gudu.xsd.modules.menu.Menu();
        m.setName("食集");
        dto.setMenu(m);
        MenuDish md = new MenuDish();
        md.setDishId(FOREIGN);
        dto.setDishes(List.of(md));
        assert404(() -> svc.saveWithDishes(dto));
        assert404(() -> svc.addDishes(5L, List.of(FOREIGN), 700L));
        verify(menuDish, never()).insert(any(MenuDish.class));
        when(menuDish.selectList(any())).thenReturn(List.of());
        assertThat(svc.addDishes(5L, List.of(PUBLIC), 700L)).containsExactly(PUBLIC);
    }

    // ------------------------------------------------------------------ 采购生成

    @Test
    void 采购_按菜谱生成() {
        ShoppingService svc = mock(ShoppingService.class, CALLS_REAL_METHODS);
        svc.setDishGuard(guard);
        assert404(() -> svc.generate("dish", null, List.of(FOREIGN)));
        assert404(() -> svc.generate("dish", null, List.of(PUBLIC, FOREIGN)));
    }

    // ------------------------------------------------------------------ 做菜（单菜直做 / 确认弹窗；Agent MarkCookedTool 同入口）

    @Test
    void 做菜_单菜直做与用料弹窗() {
        DishService dishService = mock(DishService.class);
        when(dishService.getById(FOREIGN)).thenReturn(null);
        CookingRecordAppService records = mock(CookingRecordAppService.class);
        CookAppService app = new CookAppService(dishService, mock(CookService.class), records,
                mock(com.gudu.xsd.common.domain.DomainEventPublisher.class));
        assert404(() -> app.cookDish(FOREIGN, 700L, BigDecimal.ONE, List.of(), List.of(), null));
        verifyNoInteractions(records);

        CookService cook = new CookService(null, null, null, null, dishService, null, null, null, null, null);
        assert404(() -> cook.dishMaterials(FOREIGN, BigDecimal.ONE));
    }

    // ------------------------------------------------------------------ 一周餐程换菜（Agent PlanWeekTool 同服务）

    @Test
    void 餐程_换菜() {
        MealPlanAppService svc = mock(MealPlanAppService.class, CALLS_REAL_METHODS);
        svc.setDishGuard(guard);
        assert404(() -> svc.replace(700L, 1L, 1L, FOREIGN));
    }

    // ------------------------------------------------------------------ 菜谱本身：读 / 改 / 删 / 复制 / 历史

    @Test
    void 菜谱_详情营养历史复制_别家404_公共可读() {
        DishService svc = spy(new DishService(null, null, null, null, null, null, null, null, null, null, null));
        doReturn(null).when(svc).getById(FOREIGN);
        Dish pub = new Dish();
        pub.setId(PUBLIC);
        doReturn(pub).when(svc).getById(PUBLIC);
        assert404(() -> svc.requireVisible(FOREIGN));
        assert404(() -> svc.detail(FOREIGN));
        assert404(() -> svc.fork(FOREIGN));
        assertThat(svc.requireVisible(PUBLIC).getId()).isEqualTo(PUBLIC);
    }

    @Test
    void 菜谱_改删_别家404_公共菜谱普通用户403_运营可改() {
        com.gudu.xsd.modules.identity.application.AccountQueryApi accounts =
                mock(com.gudu.xsd.modules.identity.application.AccountQueryApi.class);
        DishAccessGuard access = new DishAccessGuard(accounts);
        assert404(() -> access.checkWritable(null));
        Dish pub = new Dish();
        pub.setId(PUBLIC);
        assertThatThrownBy(() -> access.checkWritable(pub)).isInstanceOfSatisfying(BizException.class,
                e -> assertThat(e.getCode()).isEqualTo(403));
        Dish other = new Dish();
        other.setFamilyId(8L); // 别家私有（插件已拦，双保险）
        assert404(() -> access.checkWritable(other));
        Dish mine = new Dish();
        mine.setFamilyId(7L);
        access.checkWritable(mine);

        DishService svc = spy(new DishService(null, null, null, null, null, null, null, null, null, null, null));
        svc.setAccessGuard(access);
        doReturn(null).when(svc).getById(FOREIGN);
        assert404(() -> svc.checkWritable(FOREIGN));
        assert404(() -> svc.deleteFull(FOREIGN));
        DishSaveDTO dto = new DishSaveDTO();
        Dish upd = new Dish();
        upd.setId(FOREIGN);
        dto.setDish(upd);
        assert404(() -> svc.saveFull(dto));
    }

    @Test
    void 链接导入与新建_归当前家庭私有_不能借此写公共库() {
        DishAccessGuard access = new DishAccessGuard(mock(com.gudu.xsd.modules.identity.application.AccountQueryApi.class));
        Dish d = new Dish();
        d.setFamilyId(null);
        d.setSourceDishId(123L);
        access.assignOwnerForCreate(d);
        assertThat(d.getFamilyId()).isEqualTo(7L);
        assertThat(d.getSourceDishId()).isNull();
    }

    // ------------------------------------------------------------------ 语义检索 / 向量

    @Test
    void 语义检索_命中别家私有菜谱被过滤() {
        DishVectorService vec = mock(DishVectorService.class, CALLS_REAL_METHODS);
        try {
            var f = DishVectorService.class.getDeclaredField("dishMapper");
            f.setAccessible(true);
            f.set(vec, dishMapper);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        List<Document> hits = List.of(new Document("a", Map.of("dishId", PUBLIC)),
                new Document("b", Map.of("dishId", FOREIGN)));
        assertThat(vec.visibleOnly(hits)).extracting(d -> d.getMetadata().get("dishId")).containsExactly(PUBLIC);
    }
}
