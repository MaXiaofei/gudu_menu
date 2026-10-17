package com.gudu.xsd.modules.dish.application;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gudu.xsd.modules.dish.Dish;
import com.gudu.xsd.modules.dish.DishIngredient;
import com.gudu.xsd.modules.dish.DishSearchDTO;
import com.gudu.xsd.modules.dish.DishService;
import com.gudu.xsd.modules.pantry.application.StockCoverageAppService;
import com.gudu.xsd.modules.pantry.application.dto.CoverageView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 菜谱 × 余量覆盖率（dish 上下文应用服务）：/dish/search 回填 coverage、sort=coverage「家里够的优先」。
 * 每次请求：用料 1 次查询 + 覆盖率批量计算（食材/品类/档位各 1 次），无 N+1。
 */
@Service
@RequiredArgsConstructor
public class DishCoverageAppService {

    public static final String SORT_COVERAGE = "coverage";

    /** 契约排序：enough/total 降序，其次 missing 数升序；未算覆盖率的沉底。 */
    static final Comparator<CoverageView> BEST_FIRST = Comparator
            .comparingDouble(CoverageView::ratio).reversed()
            .thenComparingInt(c -> c.missing().size());

    private final DishService dishService;
    private final StockCoverageAppService coverageAppService;

    /** 做菜历史回填（可选注入：单测不提供时跳过）。 */
    private DishCookHistoryAppService cookHistory;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setCookHistory(DishCookHistoryAppService cookHistory) {
        this.cookHistory = cookHistory;
    }

    /**
     * @param loggedIn 是否登录（未登录 coverage 一律 null，sort=coverage 退化为默认排序）
     */
    public IPage<Dish> search(DishSearchDTO q, boolean loggedIn) {
        if (loggedIn && SORT_COVERAGE.equalsIgnoreCase(q.getSort())) {
            List<Dish> pool = new ArrayList<>(dishService.searchPool(q));
            fillCoverage(pool);
            pool.sort(Comparator.comparing(Dish::getCoverage, Comparator.nullsLast(BEST_FIRST))); // 稳定排序：同覆盖率保持新菜在前
            int total = pool.size();
            int size = (int) q.getPageSize();
            int from = Math.min((int) ((Math.max(q.getPageNum(), 1) - 1) * size), total);
            int to = Math.min(from + size, total);
            List<Dish> records = from < to ? new ArrayList<>(pool.subList(from, to)) : Collections.emptyList();
            dishService.decorateCards(records);
            Page<Dish> page = new Page<>(q.getPageNum(), q.getPageSize());
            page.setTotal(total);
            page.setRecords(records);
            if (cookHistory != null) cookHistory.fillLastCooked(records);
            return page;
        }
        IPage<Dish> result = dishService.search(q);
        if (loggedIn) fillCoverage(result.getRecords());
        if (loggedIn && cookHistory != null) cookHistory.fillLastCooked(result.getRecords());
        return result;
    }

    /** 批量回填覆盖率。 */
    public void fillCoverage(List<Dish> dishes) {
        if (dishes == null || dishes.isEmpty()) return;
        Map<Long, CoverageView> cov = coverageOf(dishes.stream().map(Dish::getId).filter(Objects::nonNull).toList());
        for (Dish d : dishes) d.setCoverage(cov.get(d.getId()));
    }

    /** dishId → 覆盖率（入参顺序）。 */
    public Map<Long, CoverageView> coverageOf(List<Long> dishIds) {
        if (dishIds == null || dishIds.isEmpty()) return Map.of();
        Map<Long, List<Long>> byDish = new LinkedHashMap<>();
        for (Long id : dishIds) byDish.put(id, new ArrayList<>());
        Map<Long, List<Long>> rows = dishService.ingredientsOf(dishIds).stream()
                .filter(di -> di.getIngredientId() != null)
                .collect(Collectors.groupingBy(DishIngredient::getDishId,
                        Collectors.mapping(DishIngredient::getIngredientId, Collectors.toList())));
        rows.forEach((k, v) -> byDish.computeIfPresent(k, (kk, list) -> { list.addAll(v); return list; }));
        return coverageAppService.coverageOf(byDish);
    }
}
