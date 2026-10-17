package com.gudu.xsd.modules.agent.application.kitchen;

import com.gudu.xsd.modules.agent.domain.port.Kitchen;
import com.gudu.xsd.modules.cookbook.application.CookingRecordAppService;
import com.gudu.xsd.modules.dish.DishService;
import com.gudu.xsd.modules.dish.application.DishCoverageAppService;
import com.gudu.xsd.modules.member.MemberService;
import com.gudu.xsd.modules.menu.CookService;
import com.gudu.xsd.modules.menu.MenuService;
import com.gudu.xsd.modules.menu.application.CookAppService;
import com.gudu.xsd.modules.nutrition.IngredientService;
import com.gudu.xsd.modules.pantry.PantryService;
import com.gudu.xsd.modules.pantry.application.StockAppService;
import com.gudu.xsd.modules.recommend.application.HomeRecommendAppService;
import com.gudu.xsd.modules.shopping.ShoppingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;

/** 为某个成员创建线上厨房（每轮对话一个，持有当前成员 id）。 */
@Component
@RequiredArgsConstructor
public class LiveKitchenFactory {

    final DishService dishService;
    final DishCoverageAppService coverage;
    final IngredientService ingredientService;
    final MemberService memberService;
    final MenuService menuService;
    final CookService cookService;
    final CookAppService cookAppService;
    final CookingRecordAppService cookingRecords;
    final PantryService pantryService;
    final StockAppService stockAppService;
    final HomeRecommendAppService homeRecommend;
    final ShoppingService shoppingService;
    final Clock clock;

    public Kitchen forMember(Long memberId) {
        return new LiveKitchen(this, memberId);
    }
}
