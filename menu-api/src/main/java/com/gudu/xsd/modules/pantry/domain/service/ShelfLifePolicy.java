package com.gudu.xsd.modules.pantry.domain.service;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

/**
 * 保质期建议策略（纯函数）：字典 sys_dict(purchase_category).shelf_life_days 优先，
 * 未配置时按采购品类名兜底（与 V64 迁移初始化值一致）。
 */
public final class ShelfLifePolicy {

    static final Map<String, Integer> DEFAULT_DAYS = Map.ofEntries(
            Map.entry("蔬菜", 5),
            Map.entry("水果", 7),
            Map.entry("畜禽肉", 3),
            Map.entry("水产海鲜", 2),
            Map.entry("蛋类", 30),
            Map.entry("豆制品", 3),
            Map.entry("乳制品", 7),
            Map.entry("调味料", 180),
            Map.entry("谷物主食", 180),
            Map.entry("坚果", 90),
            Map.entry("干菌藻", 180),
            Map.entry("速冻", 90),
            Map.entry("饮料", 180));

    /**
     * @param configuredDays 字典配置的天数（可空，≤0 视为未配置）
     * @param categoryName   采购品类名（可空）
     */
    public Optional<Integer> suggestedDays(Integer configuredDays, String categoryName) {
        if (configuredDays != null && configuredDays > 0) return Optional.of(configuredDays);
        if (categoryName == null) return Optional.empty();
        return Optional.ofNullable(DEFAULT_DAYS.get(categoryName.trim()));
    }

    /** 建议保质期 = 今天 + 天数。 */
    public LocalDate suggestBestBefore(LocalDate today, int days) {
        return today.plusDays(days);
    }
}
