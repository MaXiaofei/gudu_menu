package com.gudu.xsd.modules.nutrition.application;

import com.gudu.xsd.modules.nutrition.Ingredient;
import com.gudu.xsd.modules.nutrition.IngredientService;
import com.gudu.xsd.modules.nutrition.application.IngredientFrequencyAppService.FrequentIngredient;
import com.gudu.xsd.modules.nutrition.domain.repository.IngredientUsageStats;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IngredientFrequencyAppServiceTest {

    private static Ingredient ing(long id, String name) {
        Ingredient i = new Ingredient();
        i.setId(id);
        i.setName(name);
        return i;
    }

    @Test
    void 按频次排序_被删食材跳过_不足用食材库补齐() {
        IngredientUsageStats stats = mock(IngredientUsageStats.class);
        IngredientService ingSvc = mock(IngredientService.class);
        when(stats.cookedUsageByMember(7L)).thenReturn(Map.of(2L, 3));
        when(stats.recipeUsage()).thenReturn(Map.of(1L, 1, 9L, 4));
        when(ingSvc.listByIds(any())).thenReturn(List.of(ing(1, "番茄"), ing(2, "鸡蛋"))); // 9 已删
        when(ingSvc.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenReturn(List.of(ing(1, "番茄"), ing(3, "葱"), ing(4, "姜")));

        List<FrequentIngredient> out = new IngredientFrequencyAppService(stats, ingSvc).frequent(7L, 3);

        assertThat(out).extracting(FrequentIngredient::name).containsExactly("鸡蛋", "番茄", "葱");
    }

    @Test
    void 无任何数据_回退食材库前N_limit夹紧() {
        IngredientUsageStats stats = mock(IngredientUsageStats.class);
        IngredientService ingSvc = mock(IngredientService.class);
        when(stats.cookedUsageByMember(7L)).thenReturn(Map.of());
        when(stats.recipeUsage()).thenReturn(Map.of());
        when(ingSvc.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenReturn(List.of(ing(1, "番茄")));

        assertThat(new IngredientFrequencyAppService(stats, ingSvc).frequent(7L, 0))
                .containsExactly(new FrequentIngredient(1L, "番茄"));
        verify(ingSvc, never()).listByIds(any());
    }

    @Test
    void 频次已够_不查回退() {
        IngredientUsageStats stats = mock(IngredientUsageStats.class);
        IngredientService ingSvc = mock(IngredientService.class);
        when(stats.recipeUsage()).thenReturn(Map.of(1L, 2));
        when(ingSvc.listByIds(any())).thenReturn(List.of(ing(1, "番茄")));
        assertThat(new IngredientFrequencyAppService(stats, ingSvc).frequent(7L, 1)).hasSize(1);
        verify(ingSvc, never()).list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
    }
}
