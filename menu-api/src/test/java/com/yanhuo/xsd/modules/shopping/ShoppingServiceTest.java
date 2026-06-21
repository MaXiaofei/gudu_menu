package com.yanhuo.xsd.modules.shopping;

import com.yanhuo.xsd.modules.dict.mapper.DictMapper;
import com.yanhuo.xsd.modules.dish.mapper.DishIngredientMapper;
import com.yanhuo.xsd.modules.mealplan.mapper.MealPlanItemMapper;
import com.yanhuo.xsd.modules.mealplan.mapper.MealPlanMapper;
import com.yanhuo.xsd.modules.menu.mapper.MenuDishMapper;
import com.yanhuo.xsd.modules.nutrition.Ingredient;
import com.yanhuo.xsd.modules.nutrition.mapper.IngredientMapper;
import com.yanhuo.xsd.modules.notification.NotificationService;
import com.yanhuo.xsd.modules.shopping.mapper.ShoppingItemMapper;
import com.yanhuo.xsd.modules.shopping.mapper.ShoppingListMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * addItemCustom（V30 手动添加自定义采购项）逻辑测试。
 * 用 Mockito mock 全部 Mapper 依赖，验证「name 命中/不命中 ingredient」两条路径 + 参数校验。
 */
@ExtendWith(MockitoExtension.class)
class ShoppingServiceTest {

    @Mock ShoppingItemMapper itemMapper;
    @Mock MealPlanItemMapper mealPlanItemMapper;
    @Mock MealPlanMapper mealPlanMapper;
    @Mock DishIngredientMapper dishIngredientMapper;
    @Mock IngredientMapper ingredientMapper;
    @Mock DictMapper dictMapper;
    @Mock MenuDishMapper menuDishMapper;
    @Mock ShoppingAggregator aggregator;
    @Mock NotificationService notificationService;
    @Mock ShoppingListMapper shoppingListMapper;

    @InjectMocks
    private ShoppingService svc;

    /** 命中 ingredient：关联 ingredientId + 带出食材自身 purchaseCategoryId，customName 留空。 */
    @Test
    void 添加自定义项_命中食材_关联ingredientId并带出品类() {
        Ingredient tomato = new Ingredient();
        tomato.setId(10L);
        tomato.setName("番茄");
        tomato.setPurchaseCategoryId(24L);
        given(ingredientMapper.selectList(any())).willReturn(List.of(tomato));
        given(itemMapper.insert(any())).willAnswer(inv -> {
            ((ShoppingItem) inv.getArgument(0)).setId(66L);
            return 1;
        });

        Long id = svc.addItemCustom(3L, "番茄", new BigDecimal("2"), 40L, null);

        ArgumentCaptor<ShoppingItem> cap = ArgumentCaptor.forClass(ShoppingItem.class);
        verify(itemMapper).insert(cap.capture());
        ShoppingItem saved = cap.getValue();
        assertThat(id).isEqualTo(66L);
        assertThat(saved.getListId()).isEqualTo(3L);
        assertThat(saved.getIngredientId()).isEqualTo(10L);          // 命中关联
        assertThat(saved.getCustomName()).isNull();                  // 命中不留自定义名
        assertThat(saved.getPurchaseCategoryId()).isEqualTo(24L);    // 用食材自身品类
        assertThat(saved.getPurchaseAmount()).isEqualByComparingTo("2");
        assertThat(saved.getPurchaseUnitId()).isEqualTo(40L);
        assertThat(saved.getPurchased()).isEqualTo(0);
    }

    /** 未命中 ingredient：ingredientId 留空，name 存 custom_name，品类用前端传值。 */
    @Test
    void 添加自定义项_未命中食材_ingredientId留空name存customName() {
        given(ingredientMapper.selectList(any())).willReturn(List.of());
        given(itemMapper.insert(any())).willAnswer(inv -> {
            ((ShoppingItem) inv.getArgument(0)).setId(77L);
            return 1;
        });

        Long id = svc.addItemCustom(3L, "  老抽  ", new BigDecimal("1"), 41L, 25L);

        ArgumentCaptor<ShoppingItem> cap = ArgumentCaptor.forClass(ShoppingItem.class);
        verify(itemMapper).insert(cap.capture());
        ShoppingItem saved = cap.getValue();
        assertThat(id).isEqualTo(77L);
        assertThat(saved.getIngredientId()).isNull();               // 未命中不关联
        assertThat(saved.getCustomName()).isEqualTo("老抽");         // trim 后存名
        assertThat(saved.getPurchaseCategoryId()).isEqualTo(25L);   // 用前端传值
        assertThat(saved.getPurchaseUnitId()).isEqualTo(41L);
    }

    /** name 命中但食材自身无品类 → 回退用前端传值。 */
    @Test
    void 添加自定义项_命中食材但无品类_回退前端传值() {
        Ingredient ing = new Ingredient();
        ing.setId(11L);
        ing.setName("盐");
        ing.setPurchaseCategoryId(null);
        given(ingredientMapper.selectList(any())).willReturn(List.of(ing));

        svc.addItemCustom(3L, "盐", null, null, 26L);

        ArgumentCaptor<ShoppingItem> cap = ArgumentCaptor.forClass(ShoppingItem.class);
        verify(itemMapper).insert(cap.capture());
        assertThat(cap.getValue().getIngredientId()).isEqualTo(11L);
        assertThat(cap.getValue().getPurchaseCategoryId()).isEqualTo(26L);
    }

    @Test
    void 参数校验_listId空抛错() {
        assertThatThrownBy(() -> svc.addItemCustom(null, "土豆", null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 参数校验_name空抛错() {
        assertThatThrownBy(() -> svc.addItemCustom(3L, "   ", null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
