package com.gudu.xsd.modules.menu;

import com.gudu.xsd.common.domain.DomainEvent;
import com.gudu.xsd.common.domain.DomainEventPublisher;
import com.gudu.xsd.modules.cookbook.application.CookingRecordAppService;
import com.gudu.xsd.modules.dict.DictService;
import com.gudu.xsd.modules.dict.SysDict;
import com.gudu.xsd.modules.dish.Dish;
import com.gudu.xsd.modules.dish.DishIngredient;
import com.gudu.xsd.modules.dish.DishService;
import com.gudu.xsd.modules.menu.domain.event.DishCookedEvent;
import com.gudu.xsd.modules.menu.domain.model.CookSource;
import com.gudu.xsd.modules.menu.mapper.MenuDishMapper;
import com.gudu.xsd.modules.menu.mapper.MenuMapper;
import com.gudu.xsd.modules.menu.prep.MenuPrepStatus;
import com.gudu.xsd.modules.menu.prep.mapper.MenuPrepStatusMapper;
import com.gudu.xsd.modules.nutrition.Ingredient;
import com.gudu.xsd.modules.nutrition.IngredientService;
import com.gudu.xsd.modules.pantry.application.StockAppService;
import com.gudu.xsd.modules.pantry.domain.model.StockLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 做菜确认测试（feature-gaps-closure 事件化）：cookByMenu 发布 DishCookedEvent（pantry/cookbook 订阅），
 * 不再直连 PantryService / CookingRecordMapper；cookMaterials 跨模块读取全部走对方 Service。
 */
class CookServiceTest {

    @Mock MenuMapper menuMapper;
    @Mock MenuDishMapper menuDishMapper;
    @Mock MenuPrepStatusMapper menuPrepStatusMapper;
    @Mock DishService dishService;
    @Mock IngredientService ingredientService;
    @Mock DictService dictService;
    @Mock StockAppService stockAppService;
    @Mock CookingRecordAppService cookingRecordAppService;
    @Mock DomainEventPublisher eventPublisher;

    private CookService cookService;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        cookService = new CookService(menuMapper, menuDishMapper, new NeedAggregator(), menuPrepStatusMapper,
                dishService, ingredientService, dictService, stockAppService, cookingRecordAppService, eventPublisher);
        when(cookingRecordAppService.recordIdsOf(anyString())).thenReturn(List.of(1L));
    }

    private MenuDish md(long dishId, String factor) {
        MenuDish m = new MenuDish();
        m.setDishId(dishId);
        m.setServingFactor(new BigDecimal(factor));
        return m;
    }

    private DishIngredient di(long dishId, long ingId, String amount, Long unitId) {
        DishIngredient d = new DishIngredient();
        d.setDishId(dishId);
        d.setIngredientId(ingId);
        d.setAmount(new BigDecimal(amount));
        d.setUnitId(unitId);
        return d;
    }

    private Menu menu(long id) {
        Menu m = new Menu();
        m.setId(id);
        return m;
    }

    private DishCookedEvent publishedEvent() {
        ArgumentCaptor<DomainEvent> cap = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher).publish(cap.capture());
        assertThat(cap.getValue()).isInstanceOf(DishCookedEvent.class);
        return (DishCookedEvent) cap.getValue();
    }

    // ===================== cookByMenu（事件化） =====================

    @Test
    void cookByMenu_发布DishCookedEvent_携带用材确认与每道菜份数() {
        when(menuMapper.selectById(7L)).thenReturn(menu(7L));
        when(menuDishMapper.selectList(any())).thenReturn(List.of(md(1, "2")));
        when(dishService.ingredientsOf(any())).thenReturn(List.of(di(1, 10, "100", null), di(1, 20, "50", null)));
        // DomainEventPublisher 是接口默认方法 publishFrom → publish，mock 需走真实默认方法
        org.mockito.Mockito.doCallRealMethod().when(eventPublisher).publishFrom(any());
        org.mockito.Mockito.doCallRealMethod().when(eventPublisher).publishAll(any());

        CookResult r = cookService.cookByMenu(7L, 99L, List.of(10L), List.of(20L));

        assertThat(r.menuId()).isEqualTo(7L);
        assertThat(r.cookingRecordIds()).containsExactly(1L);
        DishCookedEvent e = publishedEvent();
        assertThat(e.source()).isEqualTo(CookSource.MENU);
        assertThat(e.menuId()).isEqualTo(7L);
        assertThat(e.memberId()).isEqualTo(99L);
        assertThat(e.usedUp()).containsExactly(10L);
        assertThat(e.partiallyUsed()).containsExactly(20L);
        assertThat(e.dishes()).singleElement().satisfies(d -> {
            assertThat(d.dishId()).isEqualTo(1L);
            assertThat(d.servingFactor()).isEqualByComparingTo("2");
        });
        verify(cookingRecordAppService).recordIdsOf(e.eventId());
        // 食集标完成 + 备菜全 READY
        verify(menuMapper).updateById(argThat((Menu m) -> "DONE".equals(m.getStatus()) && m.getFinishedAt() != null));
        verify(menuPrepStatusMapper).insert(argThat((MenuPrepStatus mps) -> mps.getIngredientId() == 10L));
        verify(menuPrepStatusMapper).insert(argThat((MenuPrepStatus mps) -> mps.getIngredientId() == 20L));
    }

    @Test
    void cookByMenu_已完成的食集_抛异常_不发事件() {
        Menu done = menu(7L);
        done.setStatus("DONE");
        when(menuMapper.selectById(7L)).thenReturn(done);

        assertThatThrownBy(() -> cookService.cookByMenu(7L, 99L, List.of(), List.of()))
                .hasMessageContaining("已经做完");
        verify(eventPublisher, never()).publishFrom(any());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void cookByMenu_食集不存在_抛异常() {
        when(menuMapper.selectById(7L)).thenReturn(null);
        assertThatThrownBy(() -> cookService.cookByMenu(7L, 99L, null, null)).hasMessageContaining("食集不存在");
    }

    @Test
    void cookByMenu_没确认任何食材_事件用材为空_仍标完成() {
        when(menuMapper.selectById(7L)).thenReturn(menu(7L));
        when(menuDishMapper.selectList(any())).thenReturn(List.of(md(1, "1")));
        when(dishService.ingredientsOf(any())).thenReturn(List.of(di(1, 10, "100", null)));
        org.mockito.Mockito.doCallRealMethod().when(eventPublisher).publishFrom(any());
        org.mockito.Mockito.doCallRealMethod().when(eventPublisher).publishAll(any());

        cookService.cookByMenu(7L, 99L, List.of(), List.of());

        DishCookedEvent e = publishedEvent();
        assertThat(e.usedUp()).isEmpty();
        assertThat(e.partiallyUsed()).isEmpty();
        verify(menuMapper, times(1)).updateById(argThat((Menu m) -> "DONE".equals(m.getStatus())));
    }

    // ===================== cookMaterials（确认弹窗数据） =====================

    private void stubMaterials() {
        Ingredient tomato = new Ingredient();
        tomato.setId(10L);
        tomato.setName("番茄");
        tomato.setPurchaseCategoryId(24L); // 蔬菜
        Ingredient salt = new Ingredient();
        salt.setId(20L);
        salt.setName("盐");
        salt.setPurchaseCategoryId(30L); // 调味料
        when(ingredientService.listByIds(any())).thenReturn(List.of(tomato, salt));
        when(stockAppService.levelsOf(any())).thenReturn(Map.of(10L, StockLevel.ENOUGH));
        when(dictService.idsByName("purchase_category", "调味料")).thenReturn(Set.of(30L));
        Dish dish1 = new Dish();
        dish1.setId(1L);
        dish1.setName("番茄炒蛋");
        Dish dish2 = new Dish();
        dish2.setId(2L);
        dish2.setName("蛋花汤");
        when(dishService.listByIds(any())).thenReturn(List.of(dish1, dish2));
        SysDict unitG = new SysDict();
        unitG.setId(20L);
        unitG.setName("g");
        when(dictService.listByIds(any())).thenReturn(List.of(unitG));
    }

    @Test
    void cookMaterials_返回食材档位和调料标记() {
        when(menuMapper.selectById(7L)).thenReturn(menu(7L));
        when(menuDishMapper.selectList(any())).thenReturn(List.of(md(1, "2"), md(2, "1")));
        when(dishService.ingredientsOf(any())).thenReturn(List.of(
                di(1, 10, "100", 20L), di(1, 20, "30", 20L), di(2, 20, "20", 20L)));
        stubMaterials();

        CookMaterialsVO vo = cookService.cookMaterials(7L);

        assertThat(vo.menuId()).isEqualTo(7L);
        assertThat(vo.items()).hasSize(2);
        CookMaterialsVO.Item tomatoItem = vo.items().get(0);
        assertThat(tomatoItem.ingredientName()).isEqualTo("番茄");
        assertThat(tomatoItem.usageTexts()).containsExactly("番茄炒蛋 100g ×2");
        assertThat(tomatoItem.level()).isEqualTo("ENOUGH");
        assertThat(tomatoItem.isCondiment()).isFalse();
        CookMaterialsVO.Item saltItem = vo.items().get(1);
        assertThat(saltItem.usageTexts()).containsExactlyInAnyOrder("番茄炒蛋 30g ×2", "蛋花汤 20g");
        assertThat(saltItem.level()).isEqualTo("NONE"); // 没建档默认没有
        assertThat(saltItem.isCondiment()).isTrue();
    }

    @Test
    void cookMaterials_食集不存在_抛异常() {
        when(menuMapper.selectById(9L)).thenReturn(null);

        assertThatThrownBy(() -> cookService.cookMaterials(9L))
                .hasMessageContaining("食集不存在");
    }

    // ===================== dishMaterials（单菜直做弹窗） =====================

    @Test
    void dishMaterials_menuId为空_用量按份数缩放() {
        Dish d = new Dish();
        d.setId(1L);
        when(dishService.getById(1L)).thenReturn(d);
        when(dishService.ingredientsOf(any())).thenReturn(List.of(di(1, 10, "100", 20L), di(1, 20, "30", 20L)));
        stubMaterials();

        CookMaterialsVO vo = cookService.dishMaterials(1L, new BigDecimal("2"));

        assertThat(vo.menuId()).isNull();
        assertThat(vo.items()).extracting(CookMaterialsVO.Item::ingredientName).containsExactly("番茄", "盐");
        assertThat(vo.items().get(0).usageTexts()).containsExactly("番茄炒蛋 100g ×2");
    }

    @Test
    void dishMaterials_份数缺省为1() {
        Dish d = new Dish();
        d.setId(1L);
        when(dishService.getById(1L)).thenReturn(d);
        when(dishService.ingredientsOf(any())).thenReturn(List.of(di(1, 10, "100", 20L)));
        stubMaterials();

        CookMaterialsVO vo = cookService.dishMaterials(1L, null);

        assertThat(vo.items().get(0).usageTexts()).containsExactly("番茄炒蛋 100g");
    }

    @Test
    void dishMaterials_菜谱不存在_抛异常() {
        when(dishService.getById(5L)).thenReturn(null);
        assertThatThrownBy(() -> cookService.dishMaterials(5L, null)).hasMessageContaining("菜谱不存在");
    }

    @Test
    void dishMaterials_无用料_返回空列表() {
        Dish d = new Dish();
        d.setId(1L);
        when(dishService.getById(1L)).thenReturn(d);
        when(dishService.ingredientsOf(any())).thenReturn(List.of());

        assertThat(cookService.dishMaterials(1L, BigDecimal.ONE).items()).isEmpty();
    }
}
