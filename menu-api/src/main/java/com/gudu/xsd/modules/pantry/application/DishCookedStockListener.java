package com.gudu.xsd.modules.pantry.application;

import com.gudu.xsd.modules.menu.domain.event.DishCookedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** DishCookedEvent 消费方（pantry）：按用户确认回写三档（同步、发布方事务内，失败整体回滚）。 */
@Component
@RequiredArgsConstructor
public class DishCookedStockListener {

    private final StockAppService stockAppService;

    @EventListener
    public void on(DishCookedEvent event) {
        stockAppService.consume(event.usedUp(), event.partiallyUsed());
    }
}
