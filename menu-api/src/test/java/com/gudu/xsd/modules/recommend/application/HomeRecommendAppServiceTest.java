package com.gudu.xsd.modules.recommend.application;

import com.gudu.xsd.modules.cookbook.application.CookingRecordAppService;
import com.gudu.xsd.modules.cookbook.domain.model.DishCookStat;
import com.gudu.xsd.modules.dish.Dish;
import com.gudu.xsd.modules.dish.DishService;
import com.gudu.xsd.modules.dish.application.DishCoverageAppService;
import com.gudu.xsd.modules.nutrition.Ingredient;
import com.gudu.xsd.modules.nutrition.IngredientService;
import com.gudu.xsd.modules.pantry.application.StockAppService;
import com.gudu.xsd.modules.pantry.application.dto.CoverageView;
import com.gudu.xsd.modules.pantry.application.dto.ExpiringItemView;
import com.gudu.xsd.modules.recommend.application.dto.DishCard;
import com.gudu.xsd.modules.recommend.application.dto.HomeRecommendView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 首页智荐聚合：临期主推（覆盖率降序 Top3）/ 你常做 / 季节鲜，一次请求各数据批量一次。 */
class HomeRecommendAppServiceTest {

    static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    static final LocalDate TODAY = LocalDate.of(2026, 10, 16); // 十月：南瓜/莲藕/山药…

    StockAppService stock;
    DishService dishService;
    DishCoverageAppService coverage;
    CookingRecordAppService records;
    IngredientService ingredientService;
    HomeRecommendAppService svc;

    @BeforeEach
    void setUp() {
        stock = mock(StockAppService.class);
        dishService = mock(DishService.class);
        coverage = mock(DishCoverageAppService.class);
        records = mock(CookingRecordAppService.class);
        ingredientService = mock(IngredientService.class);
        Clock clock = Clock.fixed(TODAY.atStartOfDay(ZONE).toInstant(), ZONE);
        svc = new HomeRecommendAppService(stock, dishService, coverage, records, ingredientService, clock);
    }

    private static Dish dish(long id) {
        Dish d = new Dish();
        d.setId(id);
        d.setName("菜" + id);
        d.setPrepTime(id == 1 ? 5 : null);
        d.setCookTime(id == 1 ? 10 : null);
        d.setDifficulty(2);
        return d;
    }

    private static CoverageView cov(int enough, int total) {
        List<CoverageView.Missing> m = new ArrayList<>();
        for (int i = enough; i < total; i++) m.add(new CoverageView.Missing((long) i, "x"));
        return new CoverageView(enough, total, m);
    }

    private static Ingredient ing(long id) {
        Ingredient i = new Ingredient();
        i.setId(id);
        return i;
    }

    @Test
    void 有临期菠菜_主推用到菠菜的菜_覆盖率降序_最多3() {
        List<ExpiringItemView> exp = IntStream.rangeClosed(1, 6).mapToObj(i ->
                new ExpiringItemView((long) (100 + i), "食材" + i, "ENOUGH", TODAY.plusDays(1), 1)).toList();
        when(stock.expiring(3)).thenReturn(exp);
        when(dishService.dishIdsUsingIngredients(argThat(l -> l != null && l.contains(101L))))
                .thenReturn(List.of(1L, 2L, 3L, 4L));
        when(records.mostCooked(7L, 6)).thenReturn(List.of(new DishCookStat(5L, null, 9), new DishCookStat(99L, null, 1)));
        when(ingredientService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenReturn(List.of(ing(200)));
        when(dishService.dishIdsUsingIngredients(List.of(200L))).thenReturn(List.of(6L, 1L));
        when(dishService.listByIds(any())).thenReturn(List.of(dish(1), dish(2), dish(3), dish(4), dish(5), dish(6)));
        when(coverage.coverageOf(anyList())).thenReturn(Map.of(
                1L, cov(1, 2), 2L, cov(2, 2), 3L, cov(0, 2), 4L, cov(2, 3), 5L, cov(1, 1), 6L, cov(1, 3)));
        when(records.cookedCounts(any(), any())).thenReturn(Map.of(5L, 9));

        HomeRecommendView v = svc.home(7L);

        assertThat(v.expiring()).hasSize(5);
        assertThat(v.expiringPicks()).extracting(DishCard::dishId).containsExactly(2L, 4L, 1L);
        assertThat(v.frequent()).extracting(DishCard::dishId).containsExactly(5L); // 99 已删
        assertThat(v.frequent().get(0).cookedCount()).isEqualTo(9);
        assertThat(v.seasonal()).extracting(DishCard::dishId).containsExactly(6L); // 1 已在主推，去重
        DishCard one = v.expiringPicks().get(2);
        assertThat(one.cookMinutes()).isEqualTo(15);
        assertThat(one.coverage().enough()).isEqualTo(1);
        assertThat(v.expiringPicks().get(0).cookMinutes()).isNull();
        verify(dishService, times(1)).listByIds(any());
        verify(coverage, times(1)).coverageOf(anyList());
    }

    @Test
    void 全无数据_各分区空数组_不查菜() {
        when(stock.expiring(3)).thenReturn(List.of());
        when(dishService.dishIdsUsingIngredients(any())).thenReturn(List.of());
        when(records.mostCooked(7L, 6)).thenReturn(List.of());
        when(ingredientService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of());

        HomeRecommendView v = svc.home(7L);

        assertThat(v.expiring()).isEmpty();
        assertThat(v.expiringPicks()).isEmpty();
        assertThat(v.frequent()).isEmpty();
        assertThat(v.seasonal()).isEmpty();
        verify(dishService, never()).listByIds(any());
    }
}
