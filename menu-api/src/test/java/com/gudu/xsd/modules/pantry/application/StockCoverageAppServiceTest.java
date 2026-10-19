package com.gudu.xsd.modules.pantry.application;

import com.gudu.xsd.modules.dict.DictService;
import com.gudu.xsd.modules.nutrition.Ingredient;
import com.gudu.xsd.modules.nutrition.IngredientService;
import com.gudu.xsd.modules.pantry.application.dto.CoverageView;
import com.gudu.xsd.modules.pantry.domain.model.StockLevel;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 覆盖率批量计算：多道菜只查一次食材/品类/档位（无 N+1）。 */
class StockCoverageAppServiceTest {

    private static Ingredient ing(long id, String name, Long cat) {
        Ingredient i = new Ingredient();
        i.setId(id);
        i.setName(name);
        i.setPurchaseCategoryId(cat);
        return i;
    }

    @Test
    void 多道菜批量计算_调料不计_被删食材不计() {
        StockAppService stock = mock(StockAppService.class);
        IngredientService ingSvc = mock(IngredientService.class);
        DictService dict = mock(DictService.class);
        when(ingSvc.listByIds(any())).thenReturn(List.of(ing(1, "番茄", 24L), ing(2, "鸡蛋", 27L), ing(9, "盐", 30L)));
        when(dict.idsByName("purchase_category", "调味料")).thenReturn(Set.of(30L));
        when(stock.levelsOf(any())).thenReturn(Map.of(1L, StockLevel.LOW, 9L, StockLevel.ENOUGH));
        StockCoverageAppService svc = new StockCoverageAppService(stock, ingSvc, dict);

        Map<Long, List<Long>> in = new LinkedHashMap<>();
        in.put(100L, List.of(1L, 2L, 9L));
        in.put(200L, List.of(1L, 77L)); // 77 已删
        in.put(300L, null);
        Map<Long, CoverageView> out = svc.coverageOf(in);

        assertThat(out.keySet()).containsExactly(100L, 200L, 300L);
        assertThat(out.get(100L).enough()).isEqualTo(1);
        assertThat(out.get(100L).total()).isEqualTo(2);
        assertThat(out.get(100L).missing()).extracting(CoverageView.Missing::name).containsExactly("鸡蛋");
        assertThat(out.get(200L).total()).isEqualTo(1);
        assertThat(out.get(200L).ratio()).isEqualTo(1.0);
        assertThat(out.get(300L).total()).isZero();
        assertThat(out.get(300L).ratio()).isZero();
        verify(ingSvc, times(1)).listByIds(any());
        verify(stock, times(1)).levelsOf(any());
    }

    @Test
    void 空入参直接返回() {
        StockAppService stock = mock(StockAppService.class);
        StockCoverageAppService svc = new StockCoverageAppService(stock, mock(IngredientService.class),
                mock(DictService.class));
        assertThat(svc.coverageOf(Map.of())).isEmpty();
        assertThat(svc.coverageOf(null)).isEmpty();
        verify(stock, never()).levelsOf(any());
    }
}
