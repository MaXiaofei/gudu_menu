package com.gudu.xsd.modules.nutrition.application;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gudu.xsd.modules.nutrition.Ingredient;
import com.gudu.xsd.modules.nutrition.IngredientService;
import com.gudu.xsd.modules.nutrition.domain.repository.IngredientUsageStats;
import com.gudu.xsd.modules.nutrition.domain.service.IngredientFrequencyRanker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 常用食材（G11）：按当前账号做过的菜 + 菜谱库用料频次排序；不足回退食材库前 N。 */
@Service
@RequiredArgsConstructor
public class IngredientFrequencyAppService {

    public static final int MAX_LIMIT = 30;

    public record FrequentIngredient(Long id, String name) {
    }

    private final IngredientUsageStats usageStats;
    private final IngredientService ingredientService;
    private final IngredientFrequencyRanker ranker = new IngredientFrequencyRanker();

    public List<FrequentIngredient> frequent(Long memberId, int limit) {
        int n = Math.max(1, Math.min(limit, MAX_LIMIT));
        // 多取一些，吸收被删食材后仍够 n 条
        List<Long> ranked = ranker.rank(usageStats.cookedUsageByMember(memberId), usageStats.recipeUsage(), n * 2);
        List<FrequentIngredient> out = new ArrayList<>();
        Set<Long> seen = new LinkedHashSet<>();
        if (!ranked.isEmpty()) {
            Map<Long, Ingredient> byId = ingredientService.listByIds(ranked).stream()
                    .collect(Collectors.toMap(Ingredient::getId, Function.identity(), (a, b) -> a));
            for (Long id : ranked) {
                Ingredient ing = byId.get(id);
                if (ing == null || out.size() >= n) continue;
                out.add(new FrequentIngredient(ing.getId(), ing.getName()));
                seen.add(id);
            }
        }
        if (out.size() < n) { // 回退：食材库前 N（补足）
            for (Ingredient ing : ingredientService.list(new QueryWrapper<Ingredient>()
                    .orderByAsc("id").last("LIMIT " + (n + seen.size())))) {
                if (out.size() >= n) break;
                if (seen.add(ing.getId())) out.add(new FrequentIngredient(ing.getId(), ing.getName()));
            }
        }
        return out;
    }
}
