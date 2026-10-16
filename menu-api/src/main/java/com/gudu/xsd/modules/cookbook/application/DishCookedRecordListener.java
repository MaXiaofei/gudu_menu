package com.gudu.xsd.modules.cookbook.application;

import com.gudu.xsd.modules.menu.domain.event.DishCookedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** DishCookedEvent 消费方（cookbook）：写 cooking_record（同步、发布方事务内）。 */
@Component
@RequiredArgsConstructor
public class DishCookedRecordListener {

    private final CookingRecordAppService appService;

    @EventListener
    public void on(DishCookedEvent event) {
        appService.recordCooked(event);
    }
}
