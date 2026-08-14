package com.gudu.xsd.modules.menu;

import com.gudu.xsd.modules.cookbook.CookingRecord;
import com.gudu.xsd.modules.cookbook.mapper.CookingRecordMapper;
import com.gudu.xsd.modules.dict.SysDict;
import com.gudu.xsd.modules.dict.mapper.DictMapper;
import com.gudu.xsd.modules.dish.DishIngredient;
import com.gudu.xsd.modules.dish.mapper.DishIngredientMapper;
import com.gudu.xsd.modules.menu.mapper.MenuDishMapper;
import com.gudu.xsd.modules.menu.mapper.MenuMapper;
import com.gudu.xsd.modules.menu.prep.MenuPrepStatus;
import com.gudu.xsd.modules.menu.prep.mapper.MenuPrepStatusMapper;
import com.gudu.xsd.modules.nutrition.Ingredient;
import com.gudu.xsd.modules.nutrition.mapper.IngredientMapper;
import com.gudu.xsd.modules.pantry.IngredientStock;
import com.gudu.xsd.modules.pantry.PantryService;
import com.gudu.xsd.modules.pantry.StockLog;
import com.gudu.xsd.modules.pantry.mapper.IngredientStockMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 做菜确认测试（V42 手动库存版）：cookByMenu 按 usedUp/partiallyUsed 更新档位 + 写食记 + 食集完成；
 * cookMaterials 返回本次用到的食材（档位 + 是否调料）。不再有扣减/欠量/自动勾选采购。
 */
class CookServiceTest {

    @Mock MenuMapper menuMapper;
    @Mock MenuDishMapper menuDishMapper;
    @Mock DishIngredientMapper dishIngredientMapper;
    @Mock com.gudu.xsd.modules.dish.mapper.DishMapper dishMapper;
    @Mock CookingRecordMapper cookingRecordMapper;
    @Mock PantryService pantryService;
    @Mock IngredientMapper ingredientMapper;
    @Mock IngredientStockMapper ingredientStockMapper;
    @Mock DictMapper dictMapper;
    @Mock MenuPrepStatusMapper menuPrepStatusMapper;

    private CookService cookService;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        cookService = new CookService(menuMapper, menuDishMapper, dishIngredientMapper, dishMapper,
                cookingRecordMapper, pantryService, new NeedAggregator(), ingredientMapper,
                ingredientStockMapper, dictMapper, menuPrepStatusMapper);
        // 模拟 MyBatis insert 回填 id
        when(cookingRecordMapper.insert(any(CookingRecord.class))).thenAnswer(inv -> {
            inv.getArgument(0, CookingRecord.class).setId(1L);
            return 1;
        });
    }

    private MenuDish md(long dishId, String factor) {
        MenuDish m = new MenuDish();
        m.setDishId(dishId);
        m.setServingFactor(new BigDecimal(factor));
        return m;
    }

    private DishIngredient di(long dishId, long ingId, String amount, String unitName) {
        DishIngredient d = new DishIngredient();
        d.setDishId(dishId);
        d.setIngredientId(ingId);
        d.setAmount(new BigDecimal(amount));
        d.setUnitName(unitName);
        return d;
    }

    private Menu menu(long id) {
        Menu m = new Menu();
        m.setId(id);
        return m;
    }

    // ===================== cookByMenu（确认语义） =====================

    @Test
    void cookByMenu_用完和用了一些分别更新档位() {
        when(menuMapper.selectById(7L)).thenReturn(menu(7L));
        when(menuDishMapper.selectList(any())).thenReturn(List.of(md(1, "2")));
        when(dishIngredientMapper.selectList(any())).thenReturn(List.of(di(1, 10, "100", "g"), di(1, 20, "50", "g")));

        CookResult r = cookService.cookByMenu(7L, 99L, List.of(10L), List.of(20L));

        assertThat(r.menuId()).isEqualTo(7L);
        verify(pantryService).useUp(10L, StockLog.ACTION_COOK, null);
        verify(pantryService).partialUse(20L, StockLog.ACTION_COOK_PARTIAL, null);
        // 每菜一条 cooking_record（source=menu，memo=用材总结）
        verify(cookingRecordMapper, times(1)).insert(argThat((CookingRecord rec) ->
                rec.getMenuId() == 7L && "menu".equals(rec.getSource())
                && "用完:10;用了一些:20".equals(rec.getMemo())));
        // 食集标完成
        verify(menuMapper).updateById(argThat((Menu m) -> "DONE".equals(((Menu) m).getStatus())
                && ((Menu) m).getFinishedAt() != null));
        // 备菜全 READY（聚合用料 10/20 两个食材）
        verify(menuPrepStatusMapper).insert(argThat((MenuPrepStatus mps) -> mps.getIngredientId() == 10L));
        verify(menuPrepStatusMapper).insert(argThat((MenuPrepStatus mps) -> mps.getIngredientId() == 20L));
    }

    @Test
    void cookByMenu_已完成的食集_抛异常() {
        Menu done = menu(7L);
        done.setStatus("DONE");
        when(menuMapper.selectById(7L)).thenReturn(done);

        assertThatThrownBy(() -> cookService.cookByMenu(7L, 99L, List.of(), List.of()))
                .hasMessageContaining("已经做完");
        verify(pantryService, never()).useUp(any(), any(), any());
    }

    @Test
    void cookByMenu_用户没确认任何食材_只写食记并标完成() {
        when(menuMapper.selectById(7L)).thenReturn(menu(7L));
        when(menuDishMapper.selectList(any())).thenReturn(List.of(md(1, "1")));
        when(dishIngredientMapper.selectList(any())).thenReturn(List.of(di(1, 10, "100", "g")));

        cookService.cookByMenu(7L, 99L, List.of(), List.of());

        verify(pantryService, never()).useUp(any(), any(), any());
        verify(pantryService, never()).partialUse(any(), any(), any());
        verify(cookingRecordMapper, times(1)).insert(argThat((CookingRecord rec) -> rec.getMemo() == null));
        verify(menuMapper).updateById(argThat((Menu m) -> "DONE".equals(((Menu) m).getStatus())));
    }

    // ===================== cookMaterials（确认弹窗数据） =====================

    @Test
    void cookMaterials_返回食材档位和调料标记() {
        when(menuMapper.selectById(7L)).thenReturn(menu(7L));
        when(menuDishMapper.selectList(any())).thenReturn(List.of(md(1, "2"), md(2, "1")));
        when(dishIngredientMapper.selectList(any())).thenReturn(List.of(
                di(1, 10, "100", "g"), di(1, 20, "30", "g"), di(2, 20, "20", "g")));
        Ingredient tomato = new Ingredient();
        tomato.setId(10L);
        tomato.setName("番茄");
        tomato.setPurchaseCategoryId(24L); // 蔬菜
        Ingredient salt = new Ingredient();
        salt.setId(20L);
        salt.setName("盐");
        salt.setPurchaseCategoryId(30L); // 调味料
        when(ingredientMapper.selectBatchIds(any())).thenReturn(List.of(tomato, salt));
        when(ingredientStockMapper.selectList(any())).thenReturn(List.of(
                stock(10L, IngredientStock.LEVEL_ENOUGH)));
        SysDict condiment = new SysDict();
        condiment.setId(30L);
        condiment.setName("调味料");
        when(dictMapper.selectList(any())).thenReturn(List.of(condiment));
        // 菜名回填（用量原文前缀）
        com.gudu.xsd.modules.dish.Dish dish1 = new com.gudu.xsd.modules.dish.Dish();
        dish1.setId(1L);
        dish1.setName("番茄炒蛋");
        com.gudu.xsd.modules.dish.Dish dish2 = new com.gudu.xsd.modules.dish.Dish();
        dish2.setId(2L);
        dish2.setName("蛋花汤");
        when(dishMapper.selectBatchIds(any())).thenReturn(List.of(dish1, dish2));
        // 单位名回填（fillUnitNames 查 sys_dict unit）
        SysDict unitG = new SysDict();
        unitG.setId(20L);
        unitG.setName("g");
        when(dictMapper.selectBatchIds(any())).thenReturn(List.of(unitG));

        CookMaterialsVO vo = cookService.cookMaterials(7L);

        assertThat(vo.items()).hasSize(2);
        CookMaterialsVO.Item tomatoItem = vo.items().get(0);
        assertThat(tomatoItem.ingredientName()).isEqualTo("番茄");
        // V55：用量原文（菜名 + amount + 单位），不按克合并
        assertThat(tomatoItem.usageTexts()).containsExactly("番茄炒蛋 100g ×2");
        assertThat(tomatoItem.level()).isEqualTo(IngredientStock.LEVEL_ENOUGH);
        assertThat(tomatoItem.isCondiment()).isFalse();
        CookMaterialsVO.Item saltItem = vo.items().get(1);
        assertThat(saltItem.usageTexts()).containsExactlyInAnyOrder("番茄炒蛋 30g ×2", "蛋花汤 20g");
        assertThat(saltItem.level()).isEqualTo(IngredientStock.LEVEL_NONE); // 没建档默认没有
        assertThat(saltItem.isCondiment()).isTrue();
    }

    @Test
    void cookMaterials_食集不存在_抛异常() {
        when(menuMapper.selectById(9L)).thenReturn(null);

        assertThatThrownBy(() -> cookService.cookMaterials(9L))
                .hasMessageContaining("食集不存在");
    }

    private IngredientStock stock(long id, String level) {
        IngredientStock s = new IngredientStock();
        s.setId(id);
        s.setIngredientId(id);
        s.setLevel(level);
        return s;
    }
}
