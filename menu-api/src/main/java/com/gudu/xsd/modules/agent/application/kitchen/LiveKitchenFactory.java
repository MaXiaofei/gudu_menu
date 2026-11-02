package com.gudu.xsd.modules.agent.application.kitchen;

import com.gudu.xsd.modules.agent.domain.port.Kitchen;
import com.gudu.xsd.modules.cookbook.application.CookingRecordAppService;
import com.gudu.xsd.modules.dish.DishService;
import com.gudu.xsd.modules.dish.application.DishCoverageAppService;
import com.gudu.xsd.modules.mealplan.application.MealPlanAppService;
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
    final MealPlanAppService mealPlans;
    final com.gudu.xsd.modules.mealplan.application.MealPlanSwitchPolicy mealPlanSwitch;
    final Clock clock;

    /**
     * @param platform 请求端（X-Client 判定，必须显式传）；工具里的生成类能力据此检查该端开关。
     */
    public Kitchen forMember(Long memberId, com.gudu.xsd.modules.featureswitch.domain.model.Platform platform) {
        return new LiveKitchen(this, memberId, java.util.Objects.requireNonNull(platform, "platform"));
    }

    /**
     * 不带端的厨房：只给确认 / 取消 / 撤销等只读时钟与执行补偿的路径用，这些路径不会走到任何生成类能力。
     * 若在它上面调用 mealPlanEnabled / draftWeekPlan 会按关停处理 / 抛异常（失败即关），防止漏传端。
     */
    public Kitchen forMemberWithoutSwitches(Long memberId) {
        return new LiveKitchen(this, memberId, null);
    }
}
