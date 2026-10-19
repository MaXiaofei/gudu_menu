package com.gudu.xsd.modules.pantry.domain.service;

import com.gudu.xsd.modules.pantry.domain.model.StockItem;
import com.gudu.xsd.modules.pantry.domain.model.StockLevel;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 余量统计（纯函数）：三档计数 + 3 天内到期 + 按品类计数。
 * 「3 天内到期」与 GET /pantry/expiring?days=3 同口径（含已过期、不含「用完」）。
 */
public final class StockSummaryCalculator {

    public static final int EXPIRING_DAYS = 3;

    /** 统计结果；byCategory 的 key 为品类 id（未分类为 null），按首次出现顺序。 */
    public record Summary(int enough, int low, int out, int expiring3d, Map<Long, Integer> byCategory) {
    }

    private StockSummaryCalculator() {
    }

    public static Summary summarize(List<StockItem> items, Function<Long, Long> categoryOfIngredient, LocalDate today) {
        int enough = 0, low = 0, out = 0, expiring = 0;
        Map<Long, Integer> byCategory = new LinkedHashMap<>();
        for (StockItem s : items) {
            StockLevel level = s.level();
            if (level == StockLevel.ENOUGH) enough++;
            else if (level == StockLevel.LOW) low++;
            else out++;
            if (s.isExpiringWithin(EXPIRING_DAYS, today)) expiring++;
            byCategory.merge(categoryOfIngredient.apply(s.ingredientId()), 1, Integer::sum);
        }
        return new Summary(enough, low, out, expiring, byCategory);
    }
}
