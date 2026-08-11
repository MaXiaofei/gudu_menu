package com.gudu.xsd.modules.menu;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.gudu.xsd.modules.dish.Dish;
import com.gudu.xsd.modules.dish.DishQueryService;
import com.gudu.xsd.modules.dish.mapper.DishMapper;
import com.gudu.xsd.modules.menu.MenuService.MenuDetail;
import com.gudu.xsd.modules.menu.MenuService.MenuSummary;
import com.gudu.xsd.modules.menu.mapper.MenuDishMapper;
import com.gudu.xsd.modules.menu.mapper.MenuMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 菜单服务单元测试：mock menuDishMapper / dishMapper / dishQueryService / menuCalc。
 * 覆盖 saveWithDishes（整体替换语义）、detail、summary（汇总调用纯函数）。
 */
class MenuServiceTest {

    private MenuMapper menuMapper;
    private MenuDishMapper menuDishMapper;
    private DishMapper dishMapper;
    private DishQueryService dishQueryService;
    private MenuCalcService menuCalc;
    private MenuService svc;

    @BeforeEach
    void setUp() {
        menuMapper = Mockito.mock(MenuMapper.class);
        menuDishMapper = Mockito.mock(MenuDishMapper.class);
        dishMapper = Mockito.mock(DishMapper.class);
        dishQueryService = Mockito.mock(DishQueryService.class);
        menuCalc = Mockito.mock(MenuCalcService.class);
        // 用 spy：saveWithDishes 内部调用基类 saveOrUpdate，直接 stub 整个方法
        // （saveOrUpdate 内部走 SqlHelper 需 TableInfo 缓存，非 Spring 环境下会失败）
        svc = Mockito.spy(new MenuService(menuDishMapper, dishMapper, dishQueryService, menuCalc));
        // ServiceImpl.page()/getById()/removeById 等基类方法走 baseMapper
        injectBaseMapper(svc, menuMapper);
        // 直接 stub saveOrUpdate 本身，绕过其内部 TableInfo 查询
        Mockito.doReturn(true).when(svc).saveOrUpdate(any(Menu.class));
    }

    private static void injectBaseMapper(MenuService svc, MenuMapper mapper) {
        try {
            var f = com.baomidou.mybatisplus.extension.service.impl.ServiceImpl.class.getDeclaredField("baseMapper");
            f.setAccessible(true);
            f.set(svc, mapper);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Menu menu(Long id, Integer servingCount) {
        Menu m = new Menu();
        m.setId(id);
        m.setName("测试菜单");
        m.setServingCount(servingCount);
        return m;
    }

    private MenuDish md(Long dishId, BigDecimal factor) {
        MenuDish d = new MenuDish();
        d.setDishId(dishId);
        d.setServingFactor(factor);
        return d;
    }

    // ---------------- saveWithDishes ----------------

    /** saveWithDishes：servingCount 为空时兜底 1，先删后插，关联记录 id 被清空、menuId 被回填。 */
    @Test
    void saveWithDishes_整体替换_删旧插新_并回填menuId() {
        MenuSaveDTO dto = new MenuSaveDTO();
        dto.setMenu(menu(7L, null)); // servingCount 为空
        dto.setDishes(List.of(md(1L, new BigDecimal("2")), md(2L, null)));
        // saveOrUpdate 走 baseMapper.updateById（在 setUp 中已桩）；删/插桩
        when(menuDishMapper.delete(any(Wrapper.class))).thenReturn(1);
        when(menuDishMapper.insert(any(MenuDish.class))).thenReturn(1);

        svc.saveWithDishes(dto);

        // 兜底 servingCount=1
        assertThat(dto.getMenu().getServingCount()).isEqualTo(1);
        // 先删旧关联
        verify(menuDishMapper).delete(any(Wrapper.class));
        // 插入 2 条，每条 id 清空、menuId 回填为 7
        @SuppressWarnings("unchecked")
        ArgumentCaptor<MenuDish> captor = ArgumentCaptor.forClass(MenuDish.class);
        verify(menuDishMapper, times(2)).insert(captor.capture());
        List<MenuDish> inserted = captor.getAllValues();
        assertThat(inserted).allSatisfy(md -> {
            assertThat(md.getId()).isNull();
            assertThat(md.getMenuId()).isEqualTo(7L);
        });
        assertThat(inserted.get(0).getDishId()).isEqualTo(1L);
        assertThat(inserted.get(0).getServingFactor()).isEqualByComparingTo("2");
    }

    /** saveWithDishes：dishes 为空时只删不插。 */
    @Test
    void saveWithDishes_无关联菜品_只删不插() {
        MenuSaveDTO dto = new MenuSaveDTO();
        dto.setMenu(menu(1L, 2));
        dto.setDishes(null);
        when(menuDishMapper.delete(any(Wrapper.class))).thenReturn(0);

        svc.saveWithDishes(dto);

        assertThat(dto.getMenu().getServingCount()).isEqualTo(2); // 已有值不动
        verify(menuDishMapper).delete(any(Wrapper.class));
        verify(menuDishMapper, never()).insert(any(MenuDish.class));
    }

    // ---------------- detail ----------------

    /** detail：返回菜单 + 关联菜品，每项冗余菜名/封面（消前端 N+1）。 */
    @Test
    void detail_返回菜单和关联菜品_并冗余菜名() {
        when(menuMapper.selectById(5L)).thenReturn(menu(5L, 3));
        when(menuDishMapper.selectList(any(Wrapper.class))).thenReturn(
                List.of(md(1L, BigDecimal.ONE), md(2L, BigDecimal.ONE)));
        Dish d1 = new Dish(); d1.setId(1L); d1.setName("番茄炒蛋");
        Dish d2 = new Dish(); d2.setId(2L); d2.setName("土豆丝");
        when(dishMapper.selectBatchIds(any())).thenReturn(List.of(d1, d2));

        MenuDetail detail = svc.detail(5L);

        assertThat(detail.menu().getId()).isEqualTo(5L);
        assertThat(detail.dishes()).extracting(MenuService.MenuDishVO::dishId).containsExactly(1L, 2L);
        assertThat(detail.dishes()).extracting(MenuService.MenuDishVO::dishName)
                .containsExactly("番茄炒蛋", "土豆丝");
    }

    // ---------------- summary ----------------

    /** summary：组装 MenuLine（V55 已无价格）后调用 menuCalc 营养汇总。 */
    @Test
    void summary_组装line_调用calc返回汇总() {
        // 准备：菜单有 2 道关联菜
        when(menuMapper.selectById(1L)).thenReturn(menu(1L, 1));
        MenuDish d1 = md(10L, new BigDecimal("2")); // 菜10 2份
        MenuDish d2 = md(20L, null);                 // 菜20份数null → 兜底 ONE
        when(menuDishMapper.selectList(any(Wrapper.class))).thenReturn(List.of(d1, d2));
        // detail() 批量查 dish（消 N+1）；summary 走 detail，需 mock selectBatchIds 防 NPE
        when(dishMapper.selectBatchIds(any())).thenReturn(List.of());

        Map<Long, BigDecimal> nut10 = Map.of(1L, new BigDecimal("100"));
        when(dishQueryService.nutrition(eq(10L), any())).thenReturn(nut10);
        when(dishQueryService.nutrition(eq(20L), any())).thenReturn(Map.of());

        // 桩 menuCalc 返回
        when(menuCalc.totalNutrition(any())).thenReturn(Map.of(1L, new BigDecimal("200")));

        MenuSummary summary = svc.summary(1L);

        assertThat(summary.totalNutrition()).containsEntry(1L, new BigDecimal("200"));

        // 验证 dish20 份数 null 时传给 calc 的 factor 是 ONE
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MenuCalcService.MenuLine>> captor = ArgumentCaptor.forClass(List.class);
        verify(menuCalc).totalNutrition(captor.capture());
        List<MenuCalcService.MenuLine> lines = captor.getValue();
        assertThat(lines).hasSize(2);
        assertThat(lines.get(0).servingFactor()).isEqualByComparingTo("2");
        // 第二道菜份数 null 兜底 ONE
        assertThat(lines.get(1).servingFactor()).isEqualByComparingTo("1");
    }


// ---------------- copyMenu（再做一次） ----------------

    @Test
    void copyMenu_复制食集和菜品_新建ACTIVE() {
        Menu src = menu(10L, 3);
        src.setTypeId(5L);
        src.setStatus("DONE");
        Mockito.doReturn(src).when(svc).getById(10L);
        MenuDish d1 = md(1L, new BigDecimal("2"));
        d1.setNote("少辣");
        d1.setAddedByNickname("小王");
        Mockito.doReturn(List.of(d1)).when(menuDishMapper).selectList(any(Wrapper.class));
        // spy saveWithDishes：捕获 dto 验证复制内容（不真写库）
        final MenuSaveDTO[] captured = new MenuSaveDTO[1];
        Mockito.doAnswer(inv -> {
            captured[0] = inv.getArgument(0);
            captured[0].getMenu().setId(99L); // 模拟 save 回填 id
            return null;
        }).when(svc).saveWithDishes(any(MenuSaveDTO.class));

        Long newId = svc.copyMenu(10L, 88L);

        assertThat(newId).isEqualTo(99L);
        MenuSaveDTO dto = captured[0];
        assertThat(dto.getMenu().getName()).isEqualTo("测试菜单");
        assertThat(dto.getMenu().getStatus()).isEqualTo("ACTIVE");   // 新食集进行中
        assertThat(dto.getMenu().getServingCount()).isEqualTo(3);
        assertThat(dto.getMenu().getFinishedAt()).isNull();
        assertThat(dto.getDishes()).hasSize(1);
        MenuDish copy = dto.getDishes().get(0);
        assertThat(copy.getDishId()).isEqualTo(1L);
        assertThat(copy.getServingFactor()).isEqualByComparingTo("2");
        assertThat(copy.getNote()).isEqualTo("少辣");
        assertThat(copy.getAddedByMemberId()).isEqualTo(88L); // 归新 owner
        assertThat(copy.getAddedByNickname()).isNull();
        assertThat(copy.getId()).isNull();                       // 全新行
    }

    @Test
    void copyMenu_食集不存在_抛异常() {
        Mockito.doReturn(null).when(svc).getById(99L);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> svc.copyMenu(99L, 1L))
                .hasMessageContaining("食集不存在");
    }
}

    