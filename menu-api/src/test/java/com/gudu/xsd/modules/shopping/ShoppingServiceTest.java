package com.gudu.xsd.modules.shopping;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.gudu.xsd.modules.dict.mapper.DictMapper;
import com.gudu.xsd.modules.dish.DishIngredient;
import com.gudu.xsd.modules.dish.mapper.DishIngredientMapper;
import com.gudu.xsd.modules.mealplan.mapper.MealPlanItemMapper;
import com.gudu.xsd.modules.mealplan.mapper.MealPlanMapper;
import com.gudu.xsd.modules.menu.MenuDish;
import com.gudu.xsd.modules.menu.mapper.MenuDishMapper;
import com.gudu.xsd.modules.nutrition.Ingredient;
import com.gudu.xsd.modules.nutrition.mapper.IngredientMapper;
import com.gudu.xsd.modules.notification.NotificationService;
import com.gudu.xsd.modules.pantry.IngredientStock;
import com.gudu.xsd.modules.pantry.PantryService;
import com.gudu.xsd.modules.pantry.StockLog;
import com.gudu.xsd.modules.pantry.mapper.StockLogMapper;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * 采购服务测试（V42）：addItemCustom + 档位 badge + togglePurchased 入库设档位 + fromPrep。
 * Mockito mock 全部 Mapper 依赖。
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
    @Mock PantryService pantryService;
    @Mock StockLogMapper stockLogMapper;
    @Mock ShoppingListMapper shoppingListMapper;

    @InjectMocks
    private ShoppingService svc;

    // ===================== addItemCustom（保留原逻辑） =====================

    @Test
    void 添加自定义项_命中食材_关联ingredientId并带出品类() {
        Ingredient tomato = new Ingredient();
        tomato.setId(10L);
        tomato.setName("番茄");
        tomato.setPurchaseCategoryId(24L);
        given(ingredientMapper.selectList(any())).willReturn(List.of(tomato));
        given(itemMapper.insert(any(ShoppingItem.class))).willAnswer(inv -> {
            ((ShoppingItem) inv.getArgument(0)).setId(66L);
            return 1;
        });

        Long id = svc.addItemCustom(3L, "番茄", new BigDecimal("2"), 40L, null);

        ArgumentCaptor<ShoppingItem> cap = ArgumentCaptor.forClass(ShoppingItem.class);
        verify(itemMapper).insert(cap.capture());
        ShoppingItem saved = cap.getValue();
        assertThat(id).isEqualTo(66L);
        assertThat(saved.getIngredientId()).isEqualTo(10L);
        assertThat(saved.getCustomName()).isNull();
        assertThat(saved.getPurchaseCategoryId()).isEqualTo(24L);
    }

    @Test
    void 添加自定义项_未命中食材_ingredientId留空name存customName() {
        given(ingredientMapper.selectList(any())).willReturn(List.of());
        given(itemMapper.insert(any(ShoppingItem.class))).willAnswer(inv -> {
            ((ShoppingItem) inv.getArgument(0)).setId(77L);
            return 1;
        });

        Long id = svc.addItemCustom(3L, "  老抽  ", new BigDecimal("1"), 41L, 25L);

        ArgumentCaptor<ShoppingItem> cap = ArgumentCaptor.forClass(ShoppingItem.class);
        verify(itemMapper).insert(cap.capture());
        assertThat(id).isEqualTo(77L);
        assertThat(cap.getValue().getIngredientId()).isNull();
        assertThat(cap.getValue().getCustomName()).isEqualTo("老抽");
    }

    // ===================== 档位 badge（V42） =====================

    /** getDetail 按档位标 没有/快用完/有；customName 手动加项不标记。 */
    @Test
    void getDetail_档位badge_按档位映射红黄绿() {
        ShoppingService spied = spy(svc);
        ShoppingList list = new ShoppingList();
        list.setId(1L);
        doReturn(list).when(spied).getById(1L);

        ShoppingItem tomato = item(101L, 10L);   // 无档位（没建档）→ 不标记
        ShoppingItem egg = item(102L, 20L);      // LOW → YELLOW_SHORT
        ShoppingItem salt = item(103L, 30L);     // ENOUGH → GREEN_ENOUGH
        ShoppingItem custom = new ShoppingItem();
        custom.setId(104L);
        custom.setIngredientId(null);
        custom.setCustomName("老抽");
        given(itemMapper.selectList(any())).willReturn(List.of(tomato, egg, salt, custom));

        given(ingredientMapper.selectList(any())).willReturn(List.of(
                ing(10L, "番茄"), ing(20L, "鸡蛋"), ing(30L, "盐")));
        given(pantryService.levelMap(any())).willReturn(Map.of(
                20L, IngredientStock.LEVEL_LOW,
                30L, IngredientStock.LEVEL_ENOUGH));
        given(dictMapper.selectList(any())).willReturn(List.of());

        ShoppingListVO vo = spied.getDetail(1L);

        Map<Long, ShoppingItemVO> byId = vo.getItems().stream()
                .collect(Collectors.toMap(ShoppingItemVO::getId, i -> i));
        assertThat(byId.get(101L).getStockStatus()).isNull();          // 没建档：不标记（前端标灰）
        assertThat(byId.get(102L).getStockStatus()).isEqualTo("YELLOW_SHORT"); // 快用完
        assertThat(byId.get(103L).getStockStatus()).isEqualTo("GREEN_ENOUGH"); // 有
        assertThat(byId.get(104L).getStockStatus()).isNull();          // 手动加项：不标记

        // V56：purchase_category_id 为空的项归入 0L 占位组（Map 不允许 null key，
        // 否则 Jackson 序列化抛异常 → 详情 500）；前端 categoryNames[0] 兜底「未分类/其他」
        assertThat(vo.getGrouped().keySet()).containsExactly(0L);
        assertThat(vo.getGrouped().get(0L)).hasSize(4);
        assertThat(vo.getCategoryNames().get(0L)).isEqualTo("未分类");
    }

    // ===================== togglePurchased（V42 入库设档位） =====================

    @Test
    void 勾选已买_0到1_关联食材_默认设为充足() {
        ShoppingItem it = new ShoppingItem();
        it.setId(1L);
        it.setIngredientId(10L);
        it.setPurchased(0);
        given(itemMapper.selectById(1L)).willReturn(it);

        svc.togglePurchased(1L, null);

        assertThat(it.getPurchased()).isEqualTo(1);
        verify(pantryService).setLevel(10L, IngredientStock.LEVEL_ENOUGH, StockLog.ACTION_PURCHASE, null, 1L);
    }

    @Test
    void 勾选已买_0到1_带档位参数_用指定档位() {
        ShoppingItem it = new ShoppingItem();
        it.setId(1L);
        it.setIngredientId(10L);
        it.setPurchased(0);
        given(itemMapper.selectById(1L)).willReturn(it);

        svc.togglePurchased(1L, IngredientStock.LEVEL_LOW);

        verify(pantryService).setLevel(10L, IngredientStock.LEVEL_LOW, StockLog.ACTION_PURCHASE, null, 1L);
    }

    @Test
    void 勾选已买_无食材_只标已买不入库() {
        ShoppingItem it = new ShoppingItem();
        it.setId(3L);
        it.setIngredientId(null);
        it.setCustomName("老抽");
        it.setPurchased(0);
        given(itemMapper.selectById(3L)).willReturn(it);

        svc.togglePurchased(3L, null);

        assertThat(it.getPurchased()).isEqualTo(1);
        verify(pantryService, never()).setLevel(any(), any(), any(), any(), any());
    }

    @Test
    void 取消勾选_1到0_不反向扣减() {
        ShoppingItem it = new ShoppingItem();
        it.setId(4L);
        it.setIngredientId(10L);
        it.setPurchased(1);
        given(itemMapper.selectById(4L)).willReturn(it);

        svc.togglePurchased(4L, null);

        assertThat(it.getPurchased()).isEqualTo(0);
        verify(pantryService, never()).setLevel(any(), any(), any(), any(), any());
    }

    // ===================== fromPrep（备菜一键加采购，V42） =====================

    @Test
    void fromPrep_无清单_新建清单并追加食材() {
        ShoppingService spied = spy(svc);
        doReturn(List.of()).when(spied).list(any(Wrapper.class)); // 该食集无清单
        doAnswer(inv -> {
            ((ShoppingList) inv.getArgument(0)).setId(9L);
            return true;
        }).when(spied).save(any(ShoppingList.class));
        given(itemMapper.selectCount(any())).willReturn(0L);
        given(itemMapper.insert(any(ShoppingItem.class))).willReturn(1);

        Long listId = spied.fromPrep(1L, List.of(10L));

        assertThat(listId).isEqualTo(9L);
        ArgumentCaptor<ShoppingList> listCap = ArgumentCaptor.forClass(ShoppingList.class);
        verify(spied).save(listCap.capture());
        assertThat(listCap.getValue().getSourceMenuId()).isEqualTo(1L); // 溯源
        ArgumentCaptor<ShoppingItem> itemCap = ArgumentCaptor.forClass(ShoppingItem.class);
        verify(itemMapper).insert(itemCap.capture());
        assertThat(itemCap.getValue().getIngredientId()).isEqualTo(10L);
        // V55：referenceGrams 停用不再填，totalAmount 兜底 0
        assertThat(itemCap.getValue().getReferenceGrams()).isNull();
        assertThat(itemCap.getValue().getTotalAmount()).isEqualByComparingTo("0");
    }

    @Test
    void fromPrep_已有清单_部分已在清单中_新增其余并去重() {
        ShoppingService spied = spy(svc);
        ShoppingList existing = new ShoppingList();
        existing.setId(5L);
        existing.setSourceMenuId(1L);
        doReturn(List.of(existing)).when(spied).list(any(Wrapper.class));
        // 10 不在清单（新增），11 已在清单（跳过）
        given(itemMapper.selectCount(any())).willReturn(0L, 1L);

        Long listId = spied.fromPrep(1L, List.of(10L, 11L));

        assertThat(listId).isEqualTo(5L); // 复用原清单
        ArgumentCaptor<ShoppingItem> cap = ArgumentCaptor.forClass(ShoppingItem.class);
        verify(itemMapper).insert(cap.capture());
        assertThat(cap.getValue().getIngredientId()).isEqualTo(10L);
    }

    @Test
    void fromPrep_食材都已在清单_抛异常() {
        ShoppingService spied = spy(svc);
        ShoppingList existing = new ShoppingList();
        existing.setId(5L);
        existing.setSourceMenuId(1L);
        doReturn(List.of(existing)).when(spied).list(any(Wrapper.class));
        given(itemMapper.selectCount(any())).willReturn(1L);

        assertThatThrownBy(() -> spied.fromPrep(1L, List.of(10L)))
                .hasMessageContaining("已在采购清单");
    }

    @Test
    void fromPrep_食材列表为空_抛异常() {
        assertThatThrownBy(() -> svc.fromPrep(1L, List.of()))
                .hasMessageContaining("请选择");
    }

    // ===================== restock（批量入库，B2） =====================

    @Test
    void restock_混合食材与手动项_食材入库手动项只标已买() {
        ShoppingItem tomato = new ShoppingItem();
        tomato.setId(1L);
        tomato.setIngredientId(10L);
        tomato.setPurchased(0);
        ShoppingItem custom = new ShoppingItem();
        custom.setId(2L);
        custom.setIngredientId(null);
        custom.setCustomName("老抽");
        custom.setPurchased(0);
        given(itemMapper.selectById(1L)).willReturn(tomato);
        given(itemMapper.selectById(2L)).willReturn(custom);

        ShoppingService.RestockResult r = svc.restock(List.of(1L, 2L));

        assertThat(r.restocked()).isEqualTo(1);
        assertThat(r.markedOnly()).isEqualTo(1);
        verify(pantryService).setLevel(10L, IngredientStock.LEVEL_ENOUGH, StockLog.ACTION_PURCHASE, null, 1L);
        assertThat(tomato.getPurchased()).isEqualTo(1);
        assertThat(custom.getPurchased()).isEqualTo(1);
        verify(pantryService, never()).setLevel(any(), any(), any(), any(), eq(2L));
    }

    @Test
    void restock_全部手动项_只标已买不调库存() {
        ShoppingItem custom = new ShoppingItem();
        custom.setId(3L);
        custom.setIngredientId(null);
        custom.setCustomName("洗洁精");
        custom.setPurchased(0);
        given(itemMapper.selectById(3L)).willReturn(custom);

        ShoppingService.RestockResult r = svc.restock(List.of(3L));

        assertThat(r.restocked()).isZero();
        assertThat(r.markedOnly()).isEqualTo(1);
        assertThat(custom.getPurchased()).isEqualTo(1);
        verify(pantryService, never()).setLevel(any(), any(), any(), any(), any());
    }

    @Test
    void restock_空列表_抛异常() {
        assertThatThrownBy(() -> svc.restock(List.of()))
                .hasMessageContaining("请选择");
    }

    // ===================== undoRestock（撤回入库，B3） =====================

    @Test
    void undoRestock_恢复入库前档位并删项() {
        ShoppingItem it = new ShoppingItem();
        it.setId(1L);
        it.setIngredientId(10L);
        it.setPurchased(1);
        given(itemMapper.selectById(1L)).willReturn(it);
        StockLog log = new StockLog();
        log.setBeforeLevel(IngredientStock.LEVEL_LOW);
        given(stockLogMapper.selectList(any())).willReturn(List.of(log));

        svc.undoRestock(1L);

        verify(pantryService).setLevel(10L, IngredientStock.LEVEL_LOW, StockLog.ACTION_UNDO, null, 1L);
        Long id1 = 1L;
        verify(itemMapper).deleteById((java.io.Serializable) 1L);
    }

    @Test
    void undoRestock_入库时新建档_撤回删除档位() {
        ShoppingItem it = new ShoppingItem();
        it.setId(2L);
        it.setIngredientId(20L);
        it.setPurchased(1);
        given(itemMapper.selectById(2L)).willReturn(it);
        StockLog log = new StockLog();
        log.setBeforeLevel(null); // 入库时新建档
        given(stockLogMapper.selectList(any())).willReturn(List.of(log));

        svc.undoRestock(2L);

        verify(pantryService).removeLevel(20L, StockLog.ACTION_UNDO, null, 2L);
        Long id2 = 2L;
        verify(itemMapper).deleteById((java.io.Serializable) 2L);
    }

    @Test
    void undoRestock_手动项无入库_抛异常() {
        ShoppingItem it = new ShoppingItem();
        it.setId(3L);
        it.setIngredientId(null);
        it.setCustomName("老抽");
        given(itemMapper.selectById(3L)).willReturn(it);

        assertThatThrownBy(() -> svc.undoRestock(3L))
                .hasMessageContaining("手动项");
        verify(itemMapper, never()).deleteById((java.io.Serializable) any());
    }

    @Test
    void undoRestock_无入库流水_抛异常() {
        ShoppingItem it = new ShoppingItem();
        it.setId(4L);
        it.setIngredientId(10L);
        given(itemMapper.selectById(4L)).willReturn(it);
        given(stockLogMapper.selectList(any())).willReturn(List.of());

        assertThatThrownBy(() -> svc.undoRestock(4L))
                .hasMessageContaining("没有可撤回");
        verify(itemMapper, never()).deleteById((java.io.Serializable) any());
    }

    // ===================== renameList（改名，B4） =====================

    @Test
    void renameList_改名成功() {
        ShoppingService spied = spy(svc);
        ShoppingList list = new ShoppingList();
        list.setId(9L);
        doReturn(list).when(spied).getById(9L);
        doReturn(true).when(spied).updateById(any(ShoppingList.class));

        spied.renameList(9L, "  周末采购  ");

        ArgumentCaptor<ShoppingList> cap = ArgumentCaptor.forClass(ShoppingList.class);
        verify(spied).updateById(cap.capture());
        assertThat(cap.getValue().getName()).isEqualTo("周末采购"); // trim
    }

    @Test
    void renameList_空名_抛异常() {
        assertThatThrownBy(() -> svc.renameList(9L, "  "))
                .hasMessageContaining("清单名");
    }

    // ===================== generate / getByMenu（保留） =====================

    @Test
    void generate_menu来源_存sourceMenuId() {
        given(menuDishMapper.selectList(any())).willReturn(List.of());
        given(aggregator.aggregate(any())).willReturn(List.of());
        ShoppingService spied = spy(svc);
        doReturn(true).when(spied).save(any(ShoppingList.class));

        spied.generate("menu", 1L, null);

        ArgumentCaptor<ShoppingList> cap = ArgumentCaptor.forClass(ShoppingList.class);
        verify(spied).save(cap.capture());
        assertThat(cap.getValue().getSourceMenuId()).isEqualTo(1L);
    }

    @Test
    void generate_V55后_不写referenceGrams_totalAmount兜底0() {
        // aggregator mock 返回一行（绕过真实聚合），验证落库 ShoppingItem 字段
        ShoppingAggregator.ShoppingLine line = new ShoppingAggregator.ShoppingLine(10L, 24L);
        given(aggregator.aggregate(any())).willReturn(List.of(line));
        ShoppingService spied = spy(svc);
        doReturn(true).when(spied).save(any(ShoppingList.class));

        spied.generate("menu", 1L, null);

        ArgumentCaptor<ShoppingItem> cap = ArgumentCaptor.forClass(ShoppingItem.class);
        verify(itemMapper).insert(cap.capture());
        ShoppingItem it = cap.getValue();
        assertThat(it.getIngredientId()).isEqualTo(10L);
        assertThat(it.getPurchaseCategoryId()).isEqualTo(24L);
        assertThat(it.getReferenceGrams()).isNull();        // V55 停用不再写
        assertThat(it.getTotalAmount()).isEqualByComparingTo("0"); // NOT NULL 列兜底 0
        assertThat(it.getPurchased()).isEqualTo(0);
    }

    @Test
    void getByMenu_命中_返回getDetail() {
        ShoppingService spied = spy(svc);
        ShoppingList sl = new ShoppingList();
        sl.setId(5L);
        sl.setSourceMenuId(1L);
        doReturn(List.of(sl)).when(spied).list(any(Wrapper.class));
        ShoppingListVO vo = new ShoppingListVO();
        doReturn(vo).when(spied).getDetail(5L);

        ShoppingListVO r = spied.getByMenu(1L);

        assertThat(r).isSameAs(vo);
    }

    // ===================== 辅助 =====================

    private static ShoppingItem item(long id, long ingId) {
        ShoppingItem it = new ShoppingItem();
        it.setId(id);
        it.setIngredientId(ingId);
        return it;
    }

    private static Ingredient ing(long id, String name) {
        Ingredient i = new Ingredient();
        i.setId(id);
        i.setName(name);
        return i;
    }

    private static MenuDish md(long dishId, String factor) {
        MenuDish m = new MenuDish();
        m.setDishId(dishId);
        m.setServingFactor(new BigDecimal(factor));
        return m;
    }

    private static DishIngredient di(long dishId, long ingId, String amount) {
        DishIngredient d = new DishIngredient();
        d.setDishId(dishId);
        d.setIngredientId(ingId);
        d.setAmount(new BigDecimal(amount));
        return d;
    }
}
