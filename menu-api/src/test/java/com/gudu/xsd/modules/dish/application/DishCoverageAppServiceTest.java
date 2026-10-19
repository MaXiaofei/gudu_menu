package com.gudu.xsd.modules.dish.application;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gudu.xsd.modules.dish.Dish;
import com.gudu.xsd.modules.dish.DishIngredient;
import com.gudu.xsd.modules.dish.DishSearchDTO;
import com.gudu.xsd.modules.dish.DishService;
import com.gudu.xsd.modules.pantry.application.StockCoverageAppService;
import com.gudu.xsd.modules.pantry.application.dto.CoverageView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** /dish/search 覆盖率回填 + sort=coverage（批量、无 N+1）。 */
class DishCoverageAppServiceTest {

    DishService dishService;
    StockCoverageAppService coverage;
    DishCoverageAppService svc;

    @BeforeEach
    void setUp() {
        dishService = mock(DishService.class);
        coverage = mock(StockCoverageAppService.class);
        svc = new DishCoverageAppService(dishService, coverage);
    }

    private static Dish dish(long id) {
        Dish d = new Dish();
        d.setId(id);
        d.setName("菜" + id);
        return d;
    }

    private static DishIngredient di(long dishId, long ingId) {
        DishIngredient d = new DishIngredient();
        d.setDishId(dishId);
        d.setIngredientId(ingId);
        return d;
    }

    private static CoverageView cov(int enough, int total) {
        List<CoverageView.Missing> missing = new ArrayList<>();
        for (int i = enough; i < total; i++) missing.add(new CoverageView.Missing((long) i, "缺" + i));
        return new CoverageView(enough, total, missing);
    }

    @Test
    void 未登录_不算覆盖率_coverage为null() {
        Page<Dish> page = new Page<>(1, 10);
        page.setRecords(List.of(dish(1)));
        when(dishService.search(any())).thenReturn(page);

        IPage<Dish> r = svc.search(new DishSearchDTO(), false);

        assertThat(r.getRecords().get(0).getCoverage()).isNull();
        verify(coverage, never()).coverageOf(any());
    }

    @Test
    void 登录_默认排序_当页批量回填覆盖率() {
        Page<Dish> page = new Page<>(1, 10);
        page.setRecords(new ArrayList<>(List.of(dish(1), dish(2))));
        when(dishService.search(any())).thenReturn(page);
        when(dishService.ingredientsOf(any())).thenReturn(List.of(di(1, 10), di(1, 11), di(2, 10)));
        when(coverage.coverageOf(any())).thenReturn(Map.of(1L, cov(1, 2), 2L, cov(1, 1)));

        IPage<Dish> r = svc.search(new DishSearchDTO(), true);

        assertThat(r.getRecords()).extracting(d -> d.getCoverage().enough()).containsExactly(1, 1);
        verify(dishService, times(1)).ingredientsOf(any());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<Long, List<Long>>> cap = ArgumentCaptor.forClass(Map.class);
        verify(coverage, times(1)).coverageOf(cap.capture());
        assertThat(cap.getValue().get(1L)).containsExactly(10L, 11L);
        assertThat(cap.getValue().get(2L)).containsExactly(10L);
    }

    @Test
    void 登录_sort_coverage_全池排序后分页_比例降序其次缺得少() {
        DishSearchDTO q = new DishSearchDTO();
        q.setSort("coverage");
        q.setPageNum(1);
        q.setPageSize(2);
        when(dishService.searchPool(q)).thenReturn(List.of(dish(1), dish(2), dish(3), dish(4)));
        when(dishService.ingredientsOf(any())).thenReturn(List.of());
        // 1: 1/2   2: 2/2   3: 2/4(缺2)   4: 无覆盖率
        java.util.HashMap<Long, CoverageView> m = new java.util.HashMap<>();
        m.put(1L, cov(1, 2));
        m.put(2L, cov(2, 2));
        m.put(3L, cov(2, 4));
        when(coverage.coverageOf(any())).thenReturn(m);

        IPage<Dish> r = svc.search(q, true);

        assertThat(r.getTotal()).isEqualTo(4);
        assertThat(r.getRecords()).extracting(Dish::getId).containsExactly(2L, 1L);
        verify(dishService).decorateCards(any());

        q.setPageNum(2);
        assertThat(svc.search(q, true).getRecords()).extracting(Dish::getId).containsExactly(3L, 4L);
        q.setPageNum(9);
        assertThat(svc.search(q, true).getRecords()).isEmpty();
    }

    @Test
    void 未登录_sort_coverage_退化为默认搜索() {
        DishSearchDTO q = new DishSearchDTO();
        q.setSort("coverage");
        when(dishService.search(q)).thenReturn(new Page<>(1, 10));
        svc.search(q, false);
        verify(dishService, never()).searchPool(any());
    }

    @Test
    void 空列表不查() {
        svc.fillCoverage(List.of());
        svc.fillCoverage(null);
        assertThat(svc.coverageOf(List.of())).isEmpty();
        verify(dishService, never()).ingredientsOf(any());
    }

    @Test
    void 登录后回填最近做菜_未登录不回填() {
        DishCookHistoryAppService history = mock(DishCookHistoryAppService.class);
        svc.setCookHistory(history);
        Page<Dish> page = new Page<>(1, 10);
        page.setRecords(List.of(dish(1)));
        when(dishService.search(any())).thenReturn(page);
        when(coverage.coverageOf(any())).thenReturn(java.util.Map.of());

        svc.search(new DishSearchDTO(), true);
        verify(history).fillLastCooked(page.getRecords());

        svc.search(new DishSearchDTO(), false);
        verify(history, org.mockito.Mockito.times(1)).fillLastCooked(any());
    }
}
