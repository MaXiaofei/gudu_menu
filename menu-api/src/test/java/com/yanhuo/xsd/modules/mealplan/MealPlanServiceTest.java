package com.yanhuo.xsd.modules.mealplan;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 周计划纯函数测试：detectDuplicates 按 (dishId, date, meal) 检测重复。
 * 同 MenuCalcService 范式：服务持有公开 record Item，测试 new MealPlanService(null,null,null)。
 */
class MealPlanServiceTest {

    private final MealPlanService svc = new MealPlanService(null, null, null);

    @Test
    void 检测同日同餐重复菜() {
        var items = List.of(
                new MealPlanService.Item(1L, LocalDate.of(2026, 6, 20), "午餐"),
                new MealPlanService.Item(1L, LocalDate.of(2026, 6, 20), "午餐"),  // 重复
                new MealPlanService.Item(2L, LocalDate.of(2026, 6, 20), "午餐")); // 不重复
        var dup = svc.detectDuplicates(items);
        assertThat(dup).hasSize(1);  // dishId=1 重复
    }

    @Test
    void 不同日或不同餐不算重复() {
        var items = List.of(
                new MealPlanService.Item(1L, LocalDate.of(2026, 6, 20), "午餐"),
                new MealPlanService.Item(1L, LocalDate.of(2026, 6, 21), "午餐"), // 不同日
                new MealPlanService.Item(1L, LocalDate.of(2026, 6, 20), "晚餐")); // 不同餐
        assertThat(svc.detectDuplicates(items)).isEmpty();
    }
}
