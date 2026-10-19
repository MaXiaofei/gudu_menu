package com.gudu.xsd.modules.member.application;

import com.gudu.xsd.modules.cookbook.application.CookingRecordAppService;
import com.gudu.xsd.modules.dish.DishService;
import com.gudu.xsd.modules.pantry.application.StockAppService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 「我的」统计聚合（跨 cookbook / dish / pantry 上下文只读，各走对方应用服务）。
 * 当前单家庭：统计口径为整个家庭；multi-tenant-family 落地后按当前家庭过滤。
 */
@Service
@RequiredArgsConstructor
public class MeStatsAppService {

    private final CookingRecordAppService cookingRecordAppService;
    private final DishService dishService;
    private final StockAppService stockAppService;

    public MeStatsView stats() {
        return new MeStatsView(
                cookingRecordAppService.occasionsThisMonth(),
                dishService.countFamilyDishes(),
                stockAppService.savedFromWasteThisMonth());
    }
}
