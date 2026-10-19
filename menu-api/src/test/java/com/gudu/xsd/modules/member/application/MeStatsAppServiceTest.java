package com.gudu.xsd.modules.member.application;

import com.gudu.xsd.modules.cookbook.application.CookingRecordAppService;
import com.gudu.xsd.modules.dish.DishService;
import com.gudu.xsd.modules.pantry.application.StockAppService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MeStatsAppServiceTest {

    @Test
    void 三格统计各取对应上下文() {
        CookingRecordAppService records = mock(CookingRecordAppService.class);
        DishService dishes = mock(DishService.class);
        StockAppService stock = mock(StockAppService.class);
        when(records.occasionsThisMonth()).thenReturn(21L);
        when(dishes.countFamilyDishes()).thenReturn(32L);
        when(stock.savedFromWasteThisMonth()).thenReturn(4L);

        assertThat(new MeStatsAppService(records, dishes, stock).stats())
                .isEqualTo(new MeStatsView(21, 32, 4));
    }
}
