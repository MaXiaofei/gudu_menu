package com.gudu.xsd.modules.shopping;

import com.gudu.xsd.modules.dict.mapper.DictMapper;
import com.gudu.xsd.modules.dish.mapper.DishIngredientMapper;
import com.gudu.xsd.modules.mealplan.mapper.MealPlanItemMapper;
import com.gudu.xsd.modules.mealplan.mapper.MealPlanMapper;
import com.gudu.xsd.modules.menu.mapper.MenuDishMapper;
import com.gudu.xsd.modules.nutrition.Ingredient;
import com.gudu.xsd.modules.nutrition.mapper.IngredientMapper;
import com.gudu.xsd.modules.notification.NotificationService;
import com.gudu.xsd.modules.pantry.Pantry;
import com.gudu.xsd.modules.pantry.PantryService;
import com.gudu.xsd.modules.pantry.mapper.PantryMapper;
import com.gudu.xsd.modules.shopping.mapper.ShoppingItemMapper;
import com.gudu.xsd.modules.shopping.mapper.ShoppingListMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.never;
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
    @Mock PantryMapper pantryMapper;
    @Mock PantryService pantryService;
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

    // ===================== Plan B 三色余色 =====================

    /** getDetail 按 pantry 余量给每项标 🔴没有/🟡差X/🟢够；customName 手动加项不标记。 */
    @Test
    void getDetail_三色余色_按pantry余量标红黄绿() {
        // list 1 含 4 项：番茄(ing10,ref200,pantry 无→🔴)、鸡蛋(ing20,ref100,pantry30→🟡差70)、
        // 盐(ing30,ref50,pantry80→🟢)、老抽(手动加,ing null→灰)
        ShoppingService spied = spy(svc);
        ShoppingList list = new ShoppingList();
        list.setId(1L);
        doReturn(list).when(spied).getById(1L);

        ShoppingItem tomato = item(101L, 10L, "200");
        ShoppingItem egg = item(102L, 20L, "100");
        ShoppingItem salt = item(103L, 30L, "50");
        ShoppingItem custom = new ShoppingItem();
        custom.setId(104L);
        custom.setIngredientId(null);
        custom.setCustomName("老抽");
        given(itemMapper.selectList(any())).willReturn(List.of(tomato, egg, salt, custom));

        given(ingredientMapper.selectList(any())).willReturn(List.of(
                ing(10L, "番茄"), ing(20L, "鸡蛋"), ing(30L, "盐")));
        // pantry：鸡蛋 30g、盐 80g（番茄无库存 → 🔴）
        given(pantryMapper.selectList(any())).willReturn(List.of(
                pantry(20L, "30"), pantry(30L, "80")));
        given(dictMapper.selectList(any())).willReturn(List.of());

        ShoppingListVO vo = spied.getDetail(1L);

        Map<Long, ShoppingItemVO> byId = vo.getItems().stream()
                .collect(Collectors.toMap(ShoppingItemVO::getId, i -> i));

        ShoppingItemVO t = byId.get(101L);  // 番茄： pantry 无
        assertThat(t.getStockStatus()).isEqualTo("RED_NONE");
        assertThat(t.getShortageGrams()).isEqualByComparingTo("200");
        assertThat(t.getPantryGrams()).isNull();

        ShoppingItemVO e = byId.get(102L);  // 鸡蛋：30 < 100
        assertThat(e.getStockStatus()).isEqualTo("YELLOW_SHORT");
        assertThat(e.getShortageGrams()).isEqualByComparingTo("70");
        assertThat(e.getPantryGrams()).isEqualByComparingTo("30");

        ShoppingItemVO s = byId.get(103L);  // 盐：80 >= 50
        assertThat(s.getStockStatus()).isEqualTo("GREEN_ENOUGH");
        assertThat(s.getShortageGrams()).isEqualByComparingTo("0");
        assertThat(s.getPantryGrams()).isEqualByComparingTo("80");

        ShoppingItemVO c = byId.get(104L);  // 老抽：手动加，不标记
        assertThat(c.getStockStatus()).isNull();
        assertThat(c.getPantryGrams()).isNull();
    }

    private static ShoppingItem item(long id, long ingId, String refGrams) {
        ShoppingItem it = new ShoppingItem();
        it.setId(id);
        it.setIngredientId(ingId);
        it.setReferenceGrams(new BigDecimal(refGrams));
        return it;
    }

    private static Ingredient ing(long id, String name) {
        Ingredient i = new Ingredient();
        i.setId(id);
        i.setName(name);
        return i;
    }

    // ===================== 采购回写（togglePurchased 0→1 入库 pantry） =====================

    @Test
    void 勾选已买_0到1_有食材有量_回写pantry() {
        ShoppingItem it = new ShoppingItem();
        it.setId(1L);
        it.setIngredientId(10L);
        it.setPurchaseAmount(new BigDecimal("2"));
        it.setPurchaseUnitId(40L);
        it.setReferenceGrams(new BigDecimal("1000"));
        it.setPurchased(0);
        given(itemMapper.selectById(1L)).willReturn(it);

        svc.togglePurchased(1L);

        assertThat(it.getPurchased()).isEqualTo(1);
        verify(pantryService).stockUpByIngredient(10L, new BigDecimal("2"), 40L, new BigDecimal("1000"));
    }

    @Test
    void 勾选已买_0到1_无量_用参考克数兜底回写() {
        ShoppingItem it = new ShoppingItem();
        it.setId(2L);
        it.setIngredientId(20L);
        it.setReferenceGrams(new BigDecimal("500"));
        it.setPurchased(0);
        given(itemMapper.selectById(2L)).willReturn(it);

        svc.togglePurchased(2L);

        assertThat(it.getPurchased()).isEqualTo(1);
        verify(pantryService).stockUpByIngredient(20L, null, null, new BigDecimal("500"));
    }

    @Test
    void 勾选已买_无食材_不回写pantry() {
        ShoppingItem it = new ShoppingItem();
        it.setId(3L);
        it.setIngredientId(null);
        it.setCustomName("老抽");
        it.setPurchased(0);
        given(itemMapper.selectById(3L)).willReturn(it);

        svc.togglePurchased(3L);

        assertThat(it.getPurchased()).isEqualTo(1);
        verify(pantryService, never()).stockUpByIngredient(any(), any(), any(), any());
    }

    @Test
    void 取消勾选_1到0_不反向扣减() {
        ShoppingItem it = new ShoppingItem();
        it.setId(4L);
        it.setIngredientId(10L);
        it.setPurchaseAmount(new BigDecimal("2"));
        it.setPurchaseUnitId(40L);
        it.setPurchased(1);
        given(itemMapper.selectById(4L)).willReturn(it);

        svc.togglePurchased(4L);

        assertThat(it.getPurchased()).isEqualTo(0);
        verify(pantryService, never()).stockUpByIngredient(any(), any(), any(), any());
    }

    private static Pantry pantry(long ingId, String grams) {
        Pantry p = new Pantry();
        p.setIngredientId(ingId);
        p.setGrams(new BigDecimal(grams));
        return p;
    }
}
