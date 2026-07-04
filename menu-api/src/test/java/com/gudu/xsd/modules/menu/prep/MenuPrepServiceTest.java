package com.gudu.xsd.modules.menu.prep;

import com.gudu.xsd.modules.dish.DishIngredient;
import com.gudu.xsd.modules.dish.mapper.DishIngredientMapper;
import com.gudu.xsd.modules.menu.Menu;
import com.gudu.xsd.modules.menu.MenuService;
import com.gudu.xsd.modules.menu.prep.mapper.MenuPrepStatusMapper;
import com.gudu.xsd.modules.nutrition.Ingredient;
import com.gudu.xsd.modules.nutrition.mapper.IngredientMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * MenuPrepService 单测：mock mapper，真实 PrepAggregator（已单测）。
 * 覆盖：主料装配 / 调料折叠 / 共用高亮 / 状态关联 / 进度计数 / upsert。
 * 范式照 {@link com.gudu.xsd.modules.menu.MenuServiceTest}。
 */
class MenuPrepServiceTest {

    private MenuService menuService;
    private DishIngredientMapper dishIngredientMapper;
    private IngredientMapper ingredientMapper;
    private MenuPrepStatusMapper menuPrepStatusMapper;
    private MenuPrepService svc;

    @BeforeEach
    void setUp() {
        menuService = Mockito.mock(MenuService.class);
        dishIngredientMapper = Mockito.mock(DishIngredientMapper.class);
        ingredientMapper = Mockito.mock(IngredientMapper.class);
        menuPrepStatusMapper = Mockito.mock(MenuPrepStatusMapper.class);
        svc = new MenuPrepService(menuService, dishIngredientMapper, ingredientMapper,
                menuPrepStatusMapper, new PrepAggregator());
    }

    private Menu menu(Long id) {
        Menu m = new Menu();
        m.setId(id);
        m.setName("测试食集");
        return m;
    }

    private MenuService.MenuDishVO md(Long id, Long menuId, Long dishId, String factor, String name) {
        return new MenuService.MenuDishVO(id, menuId, dishId, new BigDecimal(factor), name, null);
    }

    private DishIngredient di(Long dishId, Long ingId, String grams) {
        DishIngredient d = new DishIngredient();
        d.setDishId(dishId);
        d.setIngredientId(ingId);
        d.setGrams(new BigDecimal(grams));
        return d;
    }

    private Ingredient ing(Long id, String name, Long catId) {
        Ingredient i = new Ingredient();
        i.setId(id);
        i.setName(name);
        i.setPurchaseCategoryId(catId);
        return i;
    }

    private void stubEmpty(Long menuId) {
        when(menuService.detail(menuId)).thenReturn(
                new MenuService.MenuDetail(menu(menuId), List.of()));
    }

    // ---------------- getPrep ----------------

    /** 空食集：items/condiments 都空，进度 0。 */
    @Test
    void getPrep_空食集_返回空() {
        stubEmpty(1L);
        MenuPrepVO vo = svc.getPrep(1L);
        assertThat(vo.items()).isEmpty();
        assertThat(vo.condiments()).isEmpty();
        assertThat(vo.totalCount()).isEqualTo(0);
        assertThat(vo.readyCount()).isEqualTo(0);
    }

    /** 单菜单料（蔬菜品类）：进 items，status 默认 PENDING，调料空。 */
    @Test
    void getPrep_单菜单主料_进items_状态默认PENDING() {
        when(menuService.detail(1L)).thenReturn(new MenuService.MenuDetail(
                menu(1L), List.of(md(1L, 1L, 10L, "1", "番茄炒蛋"))));
        when(dishIngredientMapper.selectList(any())).thenReturn(List.of(di(10L, 1L, "300")));
        when(ingredientMapper.selectBatchIds(any())).thenReturn(List.of(ing(1L, "番茄", 1L)));
        when(menuPrepStatusMapper.selectList(any())).thenReturn(List.of());

        MenuPrepVO vo = svc.getPrep(1L);

        assertThat(vo.items()).hasSize(1);
        assertThat(vo.items().get(0).ingredientName()).isEqualTo("番茄");
        assertThat(vo.items().get(0).totalGrams()).isEqualByComparingTo("300");
        assertThat(vo.items().get(0).status()).isEqualTo("PENDING");
        assertThat(vo.items().get(0).shared()).isFalse();
        assertThat(vo.condiments()).isEmpty();
        assertThat(vo.totalCount()).isEqualTo(1);
        assertThat(vo.readyCount()).isEqualTo(0);
    }

    /** 调味料品类(30)：进 condiments，不计入 items/进度。 */
    @Test
    void getPrep_调味料_折叠到condiments_不计进度() {
        when(menuService.detail(1L)).thenReturn(new MenuService.MenuDetail(
                menu(1L), List.of(md(1L, 1L, 10L, "1", "清炒虾仁"))));
        when(dishIngredientMapper.selectList(any())).thenReturn(List.of(di(10L, 16L, "10")));
        when(ingredientMapper.selectBatchIds(any())).thenReturn(List.of(ing(16L, "食用油", 30L)));
        when(menuPrepStatusMapper.selectList(any())).thenReturn(List.of());

        MenuPrepVO vo = svc.getPrep(1L);

        assertThat(vo.items()).isEmpty();
        assertThat(vo.condiments()).hasSize(1);
        assertThat(vo.condiments().get(0).ingredientName()).isEqualTo("食用油");
        assertThat(vo.totalCount()).isEqualTo(0);  // 调料不计进度
    }

    /** 两菜共用一料：shared=true、dishCount=2、dishNames 含两菜、grams 合并。 */
    @Test
    void getPrep_两菜共用料_shared为true且合并() {
        when(menuService.detail(1L)).thenReturn(new MenuService.MenuDetail(
                menu(1L), List.of(
                        md(1L, 1L, 10L, "1", "番茄炒蛋"),
                        md(2L, 1L, 20L, "1", "番茄汤"))));
        when(dishIngredientMapper.selectList(any())).thenReturn(List.of(
                di(10L, 1L, "100"), di(20L, 1L, "200")));
        when(ingredientMapper.selectBatchIds(any())).thenReturn(List.of(ing(1L, "番茄", 1L)));
        when(menuPrepStatusMapper.selectList(any())).thenReturn(List.of());

        MenuPrepVO vo = svc.getPrep(1L);

        assertThat(vo.items()).hasSize(1);
        PrepItemVO item = vo.items().get(0);
        assertThat(item.shared()).isTrue();
        assertThat(item.dishCount()).isEqualTo(2);
        assertThat(item.totalGrams()).isEqualByComparingTo("300");
        assertThat(item.dishNames()).containsExactlyInAnyOrder("番茄炒蛋", "番茄汤");
    }

    /** prep_status 有 READY 记录：status 取 DB 值，readyCount=1。 */
    @Test
    void getPrep_status关联_DB有READY则取DB值计入进度() {
        when(menuService.detail(1L)).thenReturn(new MenuService.MenuDetail(
                menu(1L), List.of(md(1L, 1L, 10L, "1", "番茄炒蛋"))));
        when(dishIngredientMapper.selectList(any())).thenReturn(List.of(di(10L, 1L, "300")));
        when(ingredientMapper.selectBatchIds(any())).thenReturn(List.of(ing(1L, "番茄", 1L)));
        MenuPrepStatus ready = new MenuPrepStatus();
        ready.setMenuId(1L);
        ready.setIngredientId(1L);
        ready.setStatus("READY");
        when(menuPrepStatusMapper.selectList(any())).thenReturn(List.of(ready));

        MenuPrepVO vo = svc.getPrep(1L);

        assertThat(vo.items().get(0).status()).isEqualTo("READY");
        assertThat(vo.readyCount()).isEqualTo(1);
    }

    // ---------------- updateStatus ----------------

    /** 不存在则 insert。 */
    @Test
    void updateStatus_不存在则insert() {
        when(menuPrepStatusMapper.selectOne(any())).thenReturn(null);

        svc.updateStatus(1L, 5L, PrepStatus.READY);

        verify(menuPrepStatusMapper).insert(argThat(m ->
                m != null && m.getMenuId() == 1L && m.getIngredientId() == 5L
                        && "READY".equals(m.getStatus())));
        verify(menuPrepStatusMapper, never()).updateById(any());
    }

    /** 存在则 updateById（status 被改写）。 */
    @Test
    void updateStatus_存在则updateById() {
        MenuPrepStatus existing = new MenuPrepStatus();
        existing.setId(7L);
        existing.setMenuId(1L);
        existing.setIngredientId(5L);
        existing.setStatus("PENDING");
        when(menuPrepStatusMapper.selectOne(any())).thenReturn(existing);

        svc.updateStatus(1L, 5L, PrepStatus.READY);

        verify(menuPrepStatusMapper).updateById(existing);
        verify(menuPrepStatusMapper, never()).insert(any());
        assertThat(existing.getStatus()).isEqualTo("READY");
    }
}
