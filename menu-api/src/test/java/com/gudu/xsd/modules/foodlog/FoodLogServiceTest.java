package com.gudu.xsd.modules.foodlog;

import com.gudu.xsd.modules.cookbook.CookingRecord;
import com.gudu.xsd.modules.cookbook.mapper.CookingRecordMapper;
import com.gudu.xsd.modules.dish.Dish;
import com.gudu.xsd.modules.dish.mapper.DishMapper;
import com.gudu.xsd.modules.menu.Menu;
import com.gudu.xsd.modules.menu.MenuDish;
import com.gudu.xsd.modules.menu.mapper.MenuDishMapper;
import com.gudu.xsd.modules.menu.mapper.MenuMapper;
import com.gudu.xsd.modules.nutrition.Ingredient;
import com.gudu.xsd.modules.nutrition.mapper.IngredientMapper;
import com.gudu.xsd.modules.review.Review;
import com.gudu.xsd.modules.review.mapper.ReviewMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 食记服务测试：餐次推断 / memo 解析纯函数 + month/byDish/year/detail 查询（mock mappers）。
 */
class FoodLogServiceTest {

    @Mock CookingRecordMapper cookingRecordMapper;
    @Mock MenuMapper menuMapper;
    @Mock MenuDishMapper menuDishMapper;
    @Mock DishMapper dishMapper;
    @Mock IngredientMapper ingredientMapper;
    @Mock ReviewMapper reviewMapper;

    private FoodLogService svc;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        svc = new FoodLogService(cookingRecordMapper, menuMapper, menuDishMapper,
                dishMapper, ingredientMapper, reviewMapper);
    }

    // ===================== 纯函数 =====================

    @Test
    void 餐次推断_按时间段() {
        assertThat(FoodLogService.mealTypeOf(6)).isEqualTo("breakfast");
        assertThat(FoodLogService.mealTypeOf(9)).isEqualTo("breakfast");
        assertThat(FoodLogService.mealTypeOf(10)).isEqualTo("lunch");   // 边界：10 起午餐
        assertThat(FoodLogService.mealTypeOf(14)).isEqualTo("lunch");
        assertThat(FoodLogService.mealTypeOf(15)).isEqualTo("dinner");  // 边界：15 起晚餐
        assertThat(FoodLogService.mealTypeOf(20)).isEqualTo("dinner");
        assertThat(FoodLogService.mealTypeOf(21)).isEqualTo("snack");   // 边界：21 起加餐
        assertThat(FoodLogService.mealTypeOf(23)).isEqualTo("snack");
    }

    @Test
    void memo解析_用完和用了一些() {
        var m = FoodLogService.parseUsedMemo("用完:1,2;用了一些:3");
        assertThat(m.usedUp()).containsExactly(1L, 2L);
        assertThat(m.partial()).containsExactly(3L);
    }

    @Test
    void memo解析_只有一部分或为空() {
        assertThat(FoodLogService.parseUsedMemo("用了一些:5").usedUp()).isEmpty();
        assertThat(FoodLogService.parseUsedMemo(null).usedUp()).isEmpty();
        assertThat(FoodLogService.parseUsedMemo("").partial()).isEmpty();
        assertThat(FoodLogService.parseUsedMemo("未知格式").usedUp()).isEmpty();
    }

    // ===================== month =====================

    private CookingRecord rec(long id, Long menuId, long dishId, LocalDateTime at, String memo) {
        CookingRecord r = new CookingRecord();
        r.setId(id);
        r.setMenuId(menuId);
        r.setDishId(dishId);
        r.setCookedAt(at);
        r.setMemo(memo);
        r.setMemberId(99L);
        return r;
    }

    @Test
    void month_按食集分组_统计卡正确() {
        LocalDateTime t1 = LocalDateTime.of(2026, 7, 2, 19, 20);
        LocalDateTime t2 = LocalDateTime.of(2026, 7, 5, 12, 0);
        when(cookingRecordMapper.selectList(any())).thenReturn(List.of(
                rec(1, 10L, 1, t1, "用完:1,2;用了一些:3"),
                rec(2, 10L, 2, t1, "用完:1,2;用了一些:3"),
                rec(3, 10L, 3, t1, "用完:1,2;用了一些:3"),
                rec(4, null, 4, t2, "用完:5")));
        when(menuMapper.selectBatchIds(List.of(10L))).thenReturn(List.of(menu(10L, "今晚的饭")));
        when(dishMapper.selectBatchIds(any())).thenReturn(List.of(
                dish(1, "番茄炒蛋"), dish(2, "清蒸鲈鱼"), dish(3, "蒜蓉菠菜"), dish(4, "红烧肉")));
        when(ingredientMapper.selectBatchIds(any())).thenReturn(List.of(
                ing(1, "番茄"), ing(2, "鸡蛋"), ing(3, "盐"), ing(5, "葱")));
        when(reviewMapper.selectList(any())).thenReturn(List.of());

        FoodLogService.MonthVO vo = svc.month(99L, 2026, 7, null, null, null, 1, 20);

        // 统计卡：顿饭 2（1 食集 + 1 单菜直做）、道菜 4、做饭天数 2、最常做按 dishId 计数
        assertThat(vo.summary().meals()).isEqualTo(2);
        assertThat(vo.summary().dishes()).isEqualTo(4);
        assertThat(vo.summary().cookDays()).isEqualTo(2);
        assertThat(vo.summary().topDishes().get(0)).isEqualTo("番茄炒蛋"); // dish 1/2/3 各 1 次，取前 3

        // 时间轴（创建时间倒序，最新在上）：单菜直做(7/5) 在前 + 食集(7/2) 在后
        assertThat(vo.records()).hasSize(2);
        FoodLogService.Meal standalone = vo.records().get(0);
        assertThat(standalone.menuId()).isNull();
        assertThat(standalone.usedUpCount()).isEqualTo(1);
        FoodLogService.Meal meal = vo.records().get(1);
        assertThat(meal.menuId()).isEqualTo(10L);
        assertThat(meal.name()).isEqualTo("今晚的饭");
        assertThat(meal.dishCount()).isEqualTo(3);
        assertThat(meal.dishNames()).containsExactly("番茄炒蛋", "清蒸鲈鱼", "蒜蓉菠菜");
        assertThat(meal.usedUpCount()).isEqualTo(2);
        assertThat(meal.partialCount()).isEqualTo(1);
    }

    @Test
    void month_餐次筛选_只留晚餐() {
        when(cookingRecordMapper.selectList(any())).thenReturn(List.of(
                rec(1, null, 1, LocalDateTime.of(2026, 7, 2, 19, 20), null),
                rec(2, null, 2, LocalDateTime.of(2026, 7, 2, 12, 0), null)));
        when(dishMapper.selectBatchIds(any())).thenReturn(List.of(dish(1, "A"), dish(2, "B")));
        when(ingredientMapper.selectBatchIds(any())).thenReturn(List.of());
        when(reviewMapper.selectList(any())).thenReturn(List.of());

        FoodLogService.MonthVO vo = svc.month(99L, 2026, 7, FoodLogService.MEAL_DINNER, null, null, 1, 20);

        assertThat(vo.records()).hasSize(1);
        assertThat(vo.records().get(0).name()).isEqualTo("A");
    }

    @Test
    void month_评价状态筛选_只留已评价() {
        when(cookingRecordMapper.selectList(any())).thenReturn(List.of(
                rec(1, 10L, 1, LocalDateTime.of(2026, 7, 2, 19, 20), null),
                rec(2, 11L, 2, LocalDateTime.of(2026, 7, 2, 12, 0), null)));
        when(menuMapper.selectBatchIds(any())).thenReturn(List.of(menu(10L, "A"), menu(11L, "B")));
        when(dishMapper.selectBatchIds(any())).thenReturn(List.of(dish(1, "A1"), dish(2, "B1")));
        when(ingredientMapper.selectBatchIds(any())).thenReturn(List.of());
        Review rv = new Review();
        rv.setMenuId(10L);
        when(reviewMapper.selectList(any())).thenReturn(List.of(rv)); // 10 已评价

        FoodLogService.MonthVO vo = svc.month(99L, 2026, 7, null, null, true, 1, 20);

        assertThat(vo.records()).hasSize(1);
        assertThat(vo.records().get(0).menuId()).isEqualTo(10L);
    }

    // ===================== byDish =====================

    @Test
    void byDish_按菜聚合_次数最近均分() {
        when(cookingRecordMapper.selectList(any())).thenReturn(List.of(
                rec(1, null, 1, LocalDateTime.of(2026, 7, 2, 19, 0), null),
                rec(2, null, 1, LocalDateTime.of(2026, 7, 5, 12, 0), null),
                rec(3, null, 2, LocalDateTime.of(2026, 7, 3, 18, 0), null)));
        when(dishMapper.selectBatchIds(any())).thenReturn(List.of(dish(1, "番茄炒蛋"), dish(2, "清蒸鲈鱼")));
        when(ingredientMapper.selectBatchIds(any())).thenReturn(List.of());
        Review r1 = new Review();
        r1.setDishId(1L);
        r1.setStarRating(5);
        Review r2 = new Review();
        r2.setDishId(1L);
        r2.setStarRating(4);
        when(reviewMapper.selectList(any())).thenReturn(List.of(r1, r2));

        FoodLogService.ByDishVO vo = svc.byDish(99L, 2026, 7, null, null, null);

        assertThat(vo.totalKinds()).isEqualTo(2);
        FoodLogService.Item top = vo.items().get(0); // 次数降序：番茄炒蛋 2 次
        assertThat(top.dishName()).isEqualTo("番茄炒蛋");
        assertThat(top.count()).isEqualTo(2);
        assertThat(top.lastCookedAt()).isEqualTo(LocalDateTime.of(2026, 7, 5, 12, 0));
        assertThat(top.avgStar()).isEqualTo(4.5);
    }

    // ===================== year =====================

    @Test
    void year_12个月计数() {
        when(cookingRecordMapper.selectList(any())).thenReturn(List.of(
                rec(1, null, 1, LocalDateTime.of(2026, 7, 2, 19, 0), null),
                rec(2, null, 1, LocalDateTime.of(2026, 7, 3, 19, 0), null),
                rec(3, null, 2, LocalDateTime.of(2026, 1, 3, 12, 0), null)));

        FoodLogService.YearVO vo = svc.year(99L, 2026);

        assertThat(vo.year()).isEqualTo(2026);
        assertThat(vo.monthCounts()[0]).isEqualTo(1); // 1 月
        assertThat(vo.monthCounts()[6]).isEqualTo(2); // 7 月
        assertThat(vo.monthCounts()[11]).isEqualTo(0); // 12 月无
    }

    // ===================== detail =====================

    @Test
    void detail_菜列表_用材名称_已评状态() {
        when(menuMapper.selectById(10L)).thenReturn(menu(10L, "今晚的饭"));
        MenuDish md1 = new MenuDish();
        md1.setDishId(1L);
        md1.setServingFactor(new BigDecimal("2"));
        md1.setNote("少辣");
        when(menuDishMapper.selectList(any())).thenReturn(List.of(md1));
        when(dishMapper.selectBatchIds(any())).thenReturn(List.of(dish(1, "番茄炒蛋")));
        when(ingredientMapper.selectBatchIds(any())).thenReturn(List.of(ing(3, "盐"), ing(1, "番茄")));
        when(cookingRecordMapper.selectList(any())).thenReturn(List.of(
                rec(1, 10L, 1, LocalDateTime.of(2026, 7, 2, 19, 20), "用完:1;用了一些:3")));
        Review rv = new Review();
        rv.setMenuId(10L);
        when(reviewMapper.selectList(any())).thenReturn(List.of(rv));

        FoodLogService.DetailVO vo = svc.detail(99L, 10L);

        assertThat(vo.name()).isEqualTo("今晚的饭");
        assertThat(vo.dishes()).hasSize(1);
        assertThat(vo.dishes().get(0).dishName()).isEqualTo("番茄炒蛋");
        assertThat(vo.dishes().get(0).note()).isEqualTo("少辣");
        assertThat(vo.usedUp()).containsExactly("番茄");
        assertThat(vo.partial()).containsExactly("盐");
        assertThat(vo.reviewed()).isTrue();
    }

    @Test
    void detail_食集不存在_抛异常() {
        when(menuMapper.selectById(99L)).thenReturn(null);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> svc.detail(99L, 99L))
                .hasMessageContaining("食集不存在");
    }

    // ===================== 辅助 =====================

    private Menu menu(long id, String name) {
        Menu m = new Menu();
        m.setId(id);
        m.setName(name);
        m.setServingCount(2);
        return m;
    }

    private Dish dish(long id, String name) {
        Dish d = new Dish();
        d.setId(id);
        d.setName(name);
        return d;
    }

    private Ingredient ing(long id, String name) {
        Ingredient i = new Ingredient();
        i.setId(id);
        i.setName(name);
        return i;
    }


    @Test
    void month_全年范围_统计全年数据() {
        // month=0：只按年过滤，统计全年（跨月记录都算）
        LocalDateTime t1 = LocalDateTime.of(2026, 7, 2, 19, 20);
        LocalDateTime t2 = LocalDateTime.of(2026, 3, 5, 12, 0);
        when(cookingRecordMapper.selectList(any())).thenReturn(List.of(
                rec(1, 10L, 1, t1, "用完:1"),
                rec(2, null, 2, t2, null)));
        when(menuMapper.selectBatchIds(List.of(10L))).thenReturn(List.of(menu(10L, "今晚的饭")));
        when(dishMapper.selectBatchIds(any())).thenReturn(List.of(dish(1, "番茄炒蛋"), dish(2, "红烧肉")));
        when(ingredientMapper.selectBatchIds(any())).thenReturn(List.of());
        when(reviewMapper.selectList(any())).thenReturn(List.of());

        FoodLogService.MonthVO vo = svc.month(99L, 2026, 0, null, null, null, 1, 20);

        assertThat(vo.summary().meals()).isEqualTo(2);   // 1 食集 + 1 单菜直做（跨月）
        assertThat(vo.summary().cookDays()).isEqualTo(2);
        assertThat(vo.records()).hasSize(2);
    }


    @Test
    void month_时间轴分页_切片返回并给总数() {
        // 2 顿（1 食集 + 1 单菜直做），pageSize=1 → 每页 1 条
        LocalDateTime t1 = LocalDateTime.of(2026, 7, 2, 19, 20);
        LocalDateTime t2 = LocalDateTime.of(2026, 7, 5, 12, 0);
        when(cookingRecordMapper.selectList(any())).thenReturn(List.of(
                rec(1, 10L, 1, t1, "用完:1,2;用了一些:3"),
                rec(2, 10L, 2, t1, "用完:1,2;用了一些:3"),
                rec(3, 10L, 3, t1, "用完:1,2;用了一些:3"),
                rec(4, null, 4, t2, "用完:5")));
        when(menuMapper.selectBatchIds(List.of(10L))).thenReturn(List.of(menu(10L, "今晚的饭")));
        when(dishMapper.selectBatchIds(any())).thenReturn(List.of(
                dish(1, "番茄炒蛋"), dish(2, "清蒸鲈鱼"), dish(3, "蒜蓉菠菜"), dish(4, "红烧肉")));
        when(ingredientMapper.selectBatchIds(any())).thenReturn(List.of());
        when(reviewMapper.selectList(any())).thenReturn(List.of());

        FoodLogService.MonthVO page1 = svc.month(99L, 2026, 7, null, null, null, 1, 1);
        FoodLogService.MonthVO page2 = svc.month(99L, 2026, 7, null, null, null, 2, 1);

        assertThat(page1.total()).isEqualTo(2);
        assertThat(page1.records()).hasSize(1);
        assertThat(page1.records().get(0).menuId()).isNull();       // 第 1 页=最新(7/5 单菜直做)
        assertThat(page2.records()).hasSize(1);
        assertThat(page2.records().get(0).menuId()).isEqualTo(10L); // 第 2 页=食集那顿(7/2)
        assertThat(page1.summary().meals()).isEqualTo(2);             // 统计卡不受分页影响
    }
}

