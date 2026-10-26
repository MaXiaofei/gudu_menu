package com.gudu.xsd.modules.menu.application;

import com.gudu.xsd.common.BizException;
import com.gudu.xsd.common.domain.DomainEventPublisher;
import com.gudu.xsd.modules.cookbook.application.CookingRecordAppService;
import com.gudu.xsd.modules.dish.DishService;
import com.gudu.xsd.modules.menu.CookMaterialsVO;
import com.gudu.xsd.modules.menu.CookService;
import com.gudu.xsd.modules.menu.domain.event.DishCookedEvent;
import com.gudu.xsd.modules.menu.domain.model.CookSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 单菜直做（G1）应用服务：取菜 → CookSession 校验并完成 → 发布 DishCookedEvent
 * （cookbook 写 cooking_record(source=dish)、pantry 回写档位）→ 回传记录 id。
 */
@Service
@RequiredArgsConstructor
public class CookAppService {

    private final DishService dishService;
    private final CookService cookService;
    private final CookingRecordAppService cookingRecordAppService;
    private final DomainEventPublisher eventPublisher;

    /** 做菜确认弹窗数据（结构同食集版，menuId=null）。 */
    public CookMaterialsVO dishMaterials(Long dishId, BigDecimal servings) {
        return cookService.dishMaterials(dishId, servings);
    }

    @Transactional(rollbackFor = Exception.class)
    public CookDishResult cookDish(Long dishId, Long memberId, BigDecimal servings,
                                   List<Long> usedUp, List<Long> partiallyUsed, String note) {
        if (dishId == null || dishService.getById(dishId) == null) {
            throw BizException.notFound("菜谱不存在");
        }
        CookSession session = CookSession.forDish(memberId, dishId, servings, usedUp, partiallyUsed, note);
        DishCookedEvent event = session.complete();
        eventPublisher.publishFrom(session);
        List<Long> ids = cookingRecordAppService.recordIdsOf(event.eventId());
        if (ids.isEmpty()) throw new IllegalStateException("做菜记录未写入 eventId=" + event.eventId());
        return new CookDishResult(ids.get(0), dishId);
    }
}
