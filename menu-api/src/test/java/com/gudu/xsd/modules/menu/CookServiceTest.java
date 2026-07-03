package com.gudu.xsd.modules.menu;

import com.gudu.xsd.modules.cookbook.CookingRecord;
import com.gudu.xsd.modules.cookbook.mapper.CookingRecordMapper;
import com.gudu.xsd.modules.dish.DishIngredient;
import com.gudu.xsd.modules.dish.mapper.DishIngredientMapper;
import com.gudu.xsd.modules.menu.mapper.MenuDishMapper;
import com.gudu.xsd.modules.menu.mapper.MenuMapper;
import com.gudu.xsd.modules.pantry.PantryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CookServiceTest {

    @Mock MenuMapper menuMapper;
    @Mock MenuDishMapper menuDishMapper;
    @Mock DishIngredientMapper dishIngredientMapper;
    @Mock CookingRecordMapper cookingRecordMapper;
    @Mock PantryService pantryService;

    private CookService cookService;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        cookService = new CookService(menuMapper, menuDishMapper, dishIngredientMapper,
                cookingRecordMapper, pantryService, new NeedAggregator());
        // 模拟 MyBatis insert 回填 id（生产 @TableId(AUTO) 会回填，mock 默认不回填）
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

    private DishIngredient di(long dishId, long ingId, String grams) {
        DishIngredient d = new DishIngredient();
        d.setDishId(dishId);
        d.setIngredientId(ingId);
        d.setGrams(new BigDecimal(grams));
        return d;
    }

    @Test
    void cookByMenu_聚合扣减写record并标完成() {
        Menu menu = new Menu();
        menu.setId(7L);
        when(menuMapper.selectById(7L)).thenReturn(menu);
        when(menuDishMapper.selectList(any())).thenReturn(List.of(md(1, "2")));
        when(dishIngredientMapper.selectList(any())).thenReturn(List.of(di(1, 10, "100")));
        // 每食材扣减返回够扣
        when(pantryService.deductByIngredient(eq(10L), eq(new BigDecimal("200"))))
                .thenReturn(new PantryService.DeductResult(10L, new BigDecimal("200"), BigDecimal.ZERO, List.of()));

        CookResult r = cookService.cookByMenu(7L, 99L);

        assertThat(r.menuId()).isEqualTo(7L);
        assertThat(r.shortages()).isEmpty();
        verify(cookingRecordMapper, times(1)).insert(any(CookingRecord.class));   // 每菜一条
        verify(menuMapper).updateById(argThat(m -> "DONE".equals(((Menu) m).getStatus())
                && ((Menu) m).getFinishedAt() != null));
    }

    @Test
    void cookByMenu_欠量写入memo() {
        Menu menu = new Menu();
        menu.setId(7L);
        when(menuMapper.selectById(7L)).thenReturn(menu);
        when(menuDishMapper.selectList(any())).thenReturn(List.of(md(1, "1")));
        when(dishIngredientMapper.selectList(any())).thenReturn(List.of(
                di(1, 10, "100"), di(1, 20, "50")));
        when(pantryService.deductByIngredient(eq(10L), any())).thenReturn(
                new PantryService.DeductResult(10L, new BigDecimal("30"), new BigDecimal("70"), List.of()));
        when(pantryService.deductByIngredient(eq(20L), any())).thenReturn(
                new PantryService.DeductResult(20L, new BigDecimal("50"), BigDecimal.ZERO, List.of()));

        CookResult r = cookService.cookByMenu(7L, 99L);

        assertThat(r.shortages()).containsEntry(10L, new BigDecimal("70"));
        verify(cookingRecordMapper).insert(argThat(rec ->
                rec.getMemo() != null && rec.getMemo().contains("10:70g") && "menu".equals(rec.getSource())));
    }

    @Test
    void cookByDish_单菜直做_source为dish_不更新menu() {
        when(dishIngredientMapper.selectList(any())).thenReturn(List.of(di(3, 10, "100")));
        when(pantryService.deductByIngredient(eq(10L), eq(new BigDecimal("100"))))
                .thenReturn(new PantryService.DeductResult(10L, new BigDecimal("100"), BigDecimal.ZERO, List.of()));

        CookResult r = cookService.cookByDish(3L, BigDecimal.ONE, 99L);

        assertThat(r.menuId()).isNull();
        verify(cookingRecordMapper).insert(argThat(rec ->
                rec.getDishId() != null && rec.getDishId() == 3L
                && "dish".equals(rec.getSource()) && rec.getMenuId() == null));
        verify(menuMapper, never()).updateById(any());
    }
}
