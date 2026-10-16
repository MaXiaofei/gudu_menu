package com.gudu.xsd.modules.recommend.application;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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
import com.gudu.xsd.modules.recommend.domain.model.DishScore;
import com.gudu.xsd.modules.recommend.domain.service.RecommendRanker;
import com.gudu.xsd.modules.recommend.domain.service.SeasonalIngredientPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 首页智荐（G5）：临期主推（用到 3 天内到期食材的菜，覆盖率降序 Top3）+ 你常做（Top6）+ 季节鲜（Top6）。
 * 一次请求：菜/覆盖率/做过次数各批量一次，无 N+1。
 */
@Service
@RequiredArgsConstructor
public class HomeRecommendAppService {

    static final int EXPIRING_DAYS = 3;
    static final int EXPIRING_MAX = 5;
    static final int PICKS_MAX = 3;
    static final int FREQUENT_MAX = 6;
    static final int SEASONAL_MAX = 6;
    /** 候选池上限（防止热门食材牵出过多菜）。 */
    static final int POOL_CAP = 120;

    private final StockAppService stockAppService;
    private final DishService dishService;
    private final DishCoverageAppService dishCoverageAppService;
    private final CookingRecordAppService cookingRecordAppService;
    private final IngredientService ingredientService;
    private final Clock clock;
    private final RecommendRanker ranker = new RecommendRanker();
    private final SeasonalIngredientPolicy seasonalPolicy = new SeasonalIngredientPolicy();

    public HomeRecommendView home(Long memberId) {
        List<ExpiringItemView> expiringAll = stockAppService.expiring(EXPIRING_DAYS);
        List<ExpiringItemView> expiring = expiringAll.stream().limit(EXPIRING_MAX).toList();

        List<Long> pickPool = cap(dishService.dishIdsUsingIngredients(
                expiringAll.stream().map(ExpiringItemView::ingredientId).toList()));
        List<DishCookStat> frequentStats = cookingRecordAppService.mostCooked(memberId, FREQUENT_MAX);
        List<Long> seasonalPool = cap(dishService.dishIdsUsingIngredients(seasonalIngredientIds()));

        Set<Long> allIds = new LinkedHashSet<>(pickPool);
        frequentStats.forEach(s -> allIds.add(s.dishId()));
        allIds.addAll(seasonalPool);
        if (allIds.isEmpty()) return new HomeRecommendView(expiring, List.of(), List.of(), List.of());

        Map<Long, Dish> dishById = dishService.listByIds(allIds).stream()
                .collect(Collectors.toMap(Dish::getId, Function.identity(), (a, b) -> a));
        List<Long> visible = allIds.stream().filter(dishById::containsKey).toList();
        Map<Long, CoverageView> coverage = dishCoverageAppService.coverageOf(visible);
        Map<Long, Integer> counts = cookingRecordAppService.cookedCounts(memberId, visible);

        List<Long> pickIds = ranker.topByCoverage(scores(pickPool, dishById, coverage, counts), Set.of(), PICKS_MAX);
        List<Long> frequentIds = frequentStats.stream().map(DishCookStat::dishId)
                .filter(dishById::containsKey).limit(FREQUENT_MAX).toList();
        List<Long> seasonalIds = ranker.topByCoverage(scores(seasonalPool, dishById, coverage, counts),
                new HashSet<>(pickIds), SEASONAL_MAX);

        return new HomeRecommendView(expiring,
                cards(pickIds, dishById, coverage, counts),
                cards(frequentIds, dishById, coverage, counts),
                cards(seasonalIds, dishById, coverage, counts));
    }

    private List<Long> seasonalIngredientIds() {
        List<String> names = seasonalPolicy.ingredientNamesFor(LocalDate.now(clock).getMonth());
        if (names.isEmpty()) return List.of();
        return ingredientService.list(new QueryWrapper<Ingredient>().select("id").in("name", names))
                .stream().map(Ingredient::getId).toList();
    }

    private static List<Long> cap(List<Long> ids) {
        return ids.stream().filter(Objects::nonNull).distinct().limit(POOL_CAP).toList();
    }

    private static List<DishScore> scores(Collection<Long> ids, Map<Long, Dish> dishById,
                                          Map<Long, CoverageView> coverage, Map<Long, Integer> counts) {
        List<DishScore> out = new ArrayList<>();
        for (Long id : ids) {
            if (!dishById.containsKey(id)) continue;
            CoverageView c = coverage.get(id);
            out.add(new DishScore(id, c == null ? 0d : c.ratio(), c == null ? Integer.MAX_VALUE : c.missing().size(),
                    counts.getOrDefault(id, 0)));
        }
        return out;
    }

    private static List<DishCard> cards(List<Long> ids, Map<Long, Dish> dishById,
                                        Map<Long, CoverageView> coverage, Map<Long, Integer> counts) {
        return ids.stream().map(dishById::get).filter(Objects::nonNull).map(d -> new DishCard(
                d.getId(), d.getName(), d.getCoverUrl(), cookMinutes(d), d.getDifficulty(),
                counts.getOrDefault(d.getId(), 0), coverage.get(d.getId()))).toList();
    }

    static Integer cookMinutes(Dish d) {
        if (d.getPrepTime() == null && d.getCookTime() == null) return null;
        return (d.getPrepTime() == null ? 0 : d.getPrepTime()) + (d.getCookTime() == null ? 0 : d.getCookTime());
    }
}
