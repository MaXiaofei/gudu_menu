package com.gudu.xsd.modules.dish;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gudu.xsd.common.BizException;
import com.gudu.xsd.common.security.RequireOperator;
import com.gudu.xsd.modules.backoffice.domain.model.OperationAction;
import com.gudu.xsd.modules.backoffice.domain.model.OperationLog;
import com.gudu.xsd.modules.backoffice.domain.model.OperationTarget;
import com.gudu.xsd.modules.backoffice.domain.repository.OperationLogRepository;
import com.gudu.xsd.modules.cookbook.mapper.CookingRecordMapper;
import com.gudu.xsd.modules.dish.application.DishLibraryAppService;
import com.gudu.xsd.modules.dish.application.dto.DishLibraryViews.PageResult;
import com.gudu.xsd.modules.dish.application.dto.DishLibraryViews.Row;
import com.gudu.xsd.modules.dish.interfaces.DishLibraryAdminController;
import com.gudu.xsd.modules.dish.mapper.DishMapper;
import com.gudu.xsd.modules.menu.MenuDish;
import com.gudu.xsd.modules.menu.MenuSaveDTO;
import com.gudu.xsd.modules.menu.MenuService;
import com.gudu.xsd.modules.menu.mapper.MenuDishMapper;
import com.gudu.xsd.modules.nutrition.NutritionCalcService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.ai.document.Document;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

/** 公共菜谱下架 / 上架（V101）：运营接口规则 + 各新入口过滤 + 详情仍可读。 */
class DishOfflineTest {

    static final long OPERATOR = 9L;
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-02T02:00:00Z"), ZoneId.of("UTC"));

    private DishMapper dishMapper;
    private CookingRecordMapper cookingRecordMapper;
    private DishService dishService;
    private OperationLogRepository logs;
    private DishLibraryAppService lib;

    static Dish dish(long id, String name, Long familyId, boolean offline) {
        Dish d = new Dish();
        d.setId(id);
        d.setName(name);
        d.setFamilyId(familyId);
        if (offline) d.setOfflineAt(LocalDateTime.parse("2026-11-01T10:00:00"));
        return d;
    }

    @BeforeEach
    void setUp() {
        dishMapper = mock(DishMapper.class);
        cookingRecordMapper = mock(CookingRecordMapper.class);
        dishService = new DishService(mock(com.gudu.xsd.modules.dish.mapper.DishStepMapper.class),
                mock(com.gudu.xsd.modules.dish.mapper.DishDictMapper.class),
                mock(com.gudu.xsd.modules.dish.mapper.DishIngredientMapper.class),
                mock(com.gudu.xsd.modules.dish.mapper.DishHistoryMapper.class),
                mock(com.gudu.xsd.modules.nutrition.mapper.IngredientNutritionMapper.class),
                mock(com.gudu.xsd.modules.nutrition.mapper.IngredientMapper.class), new NutritionCalcService(),
                null, cookingRecordMapper, null, mock(DishVectorService.class));
        DishServiceTest.injectBaseMapperFor(dishService, dishMapper);
        logs = mock(OperationLogRepository.class);
        lib = new DishLibraryAppService(dishService, logs, CLOCK);
    }

    private static String segment(Object w) {
        return ((QueryWrapper<?>) w).getSqlSegment();
    }

    // ------------------------------------------------------------------ 运营接口：权限与规则

    @Test
    void 接口只对平台运营开放() {
        assertThat(DishLibraryAdminController.class.isAnnotationPresent(RequireOperator.class)).isTrue();
        assertThat(DishLibraryAdminController.class.getAnnotation(org.springframework.web.bind.annotation.RequestMapping.class)
                .value()).containsExactly("/admin/dish-library");
    }

    @Test
    void 下架_原因必填_公共菜谱成功_写操作日志_只改下架三列() {
        when(dishMapper.selectById(1L)).thenReturn(dish(1L, "番茄炒蛋", null, false));

        assertThatThrownBy(() -> lib.offline(OPERATOR, 1L, "  ")).isInstanceOf(BizException.class)
                .hasMessageContaining("下架原因");
        assertThatThrownBy(() -> lib.offline(OPERATOR, 1L, "x".repeat(201))).isInstanceOf(BizException.class);
        verify(dishMapper, never()).update(any(), any());

        long epoch = DishService.catalogEpoch();
        Row r = lib.offline(OPERATOR, 1L, " 图片侵权 ");

        assertThat(r.offline()).isTrue();
        assertThat(r.offlineReason()).isEqualTo("图片侵权");
        assertThat(r.offlineAt()).isEqualTo(LocalDateTime.parse("2026-11-02T02:00:00"));
        assertThat(DishService.catalogEpoch()).as("餐程目录缓存失效").isGreaterThan(epoch);
        ArgumentCaptor<Wrapper<Dish>> w = ArgumentCaptor.forClass(Wrapper.class);
        verify(dishMapper).update(any(), w.capture());
        assertThat(w.getValue().getSqlSet()).contains("offline_at", "offline_reason", "offline_by");
        ArgumentCaptor<OperationLog> log = ArgumentCaptor.forClass(OperationLog.class);
        verify(logs).save(log.capture());
        assertThat(log.getValue().action()).isEqualTo(OperationAction.OFFLINE_DISH);
        assertThat(log.getValue().targetType()).isEqualTo(OperationTarget.DISH);
        assertThat(log.getValue().targetId()).isEqualTo(1L);
        assertThat(log.getValue().operatorId()).isEqualTo(OPERATOR);
        assertThat(log.getValue().reason()).isEqualTo("图片侵权");
    }

    @Test
    void 下架_只限公共菜谱_家庭私有拒绝_不存在404_重复下架拒绝() {
        when(dishMapper.selectById(2L)).thenReturn(dish(2L, "我家红烧肉", 77L, false));
        when(dishMapper.selectById(3L)).thenReturn(dish(3L, "已下架菜", null, true));
        when(dishMapper.selectById(404L)).thenReturn(null);

        assertThatThrownBy(() -> lib.offline(OPERATOR, 2L, "原因")).isInstanceOf(BizException.class)
                .hasMessageContaining("公共菜谱");
        assertThatThrownBy(() -> lib.offline(OPERATOR, 404L, "原因")).isInstanceOfSatisfying(BizException.class,
                e -> assertThat(e.getCode()).isEqualTo(404));
        assertThatThrownBy(() -> lib.offline(OPERATOR, 3L, "原因")).hasMessageContaining("已经下架");
        verify(logs, never()).save(any());
    }

    @Test
    void 重新上架_清空三列_写日志_未下架或私有拒绝() {
        when(dishMapper.selectById(3L)).thenReturn(dish(3L, "已下架菜", null, true));
        when(dishMapper.selectById(1L)).thenReturn(dish(1L, "在售菜", null, false));
        when(dishMapper.selectById(2L)).thenReturn(dish(2L, "私有", 77L, true));

        assertThatThrownBy(() -> lib.online(OPERATOR, 1L, null)).hasMessageContaining("没有下架");
        assertThatThrownBy(() -> lib.online(OPERATOR, 2L, null)).hasMessageContaining("公共菜谱");

        Row r = lib.online(OPERATOR, 3L, null);

        assertThat(r.offline()).isFalse();
        assertThat(r.offlineAt()).isNull();
        ArgumentCaptor<OperationLog> log = ArgumentCaptor.forClass(OperationLog.class);
        verify(logs).save(log.capture());
        assertThat(log.getValue().action()).isEqualTo(OperationAction.ONLINE_DISH);
        assertThat(log.getValue().targetId()).isEqualTo(3L);
    }

    @Test
    void 公共菜谱库列表_只查公共_按上架状态筛选_返回状态字段() {
        Page<Dish> p = new Page<>(1, 15);
        p.setRecords(List.of(dish(3L, "已下架菜", null, true)));
        p.setTotal(1);
        when(dishMapper.selectPage(any(), any(Wrapper.class))).thenReturn(p);

        PageResult res = lib.page("番茄", DishLibraryAppService.STATUS_OFFLINE, 1, 15);

        assertThat(res.total()).isEqualTo(1);
        assertThat(res.records().get(0).offline()).isTrue();
        assertThat(res.records().get(0).offlineReason()).isNull();
        ArgumentCaptor<Wrapper<Dish>> w = ArgumentCaptor.forClass(Wrapper.class);
        verify(dishMapper).selectPage(any(), w.capture());
        assertThat(segment(w.getValue())).contains("family_id IS NULL").contains("offline_at IS NOT NULL").contains("name LIKE");

        lib.page(null, DishLibraryAppService.STATUS_ONLINE, 1, 15);
        verify(dishMapper, times(2)).selectPage(any(), w.capture());
        assertThat(segment(w.getValue())).contains("family_id IS NULL").contains("offline_at IS NULL");

        lib.page(null, null, 1, 15);
        verify(dishMapper, times(3)).selectPage(any(), w.capture());
        assertThat(segment(w.getValue())).doesNotContain("offline_at");
    }

    // ------------------------------------------------------------------ 新入口过滤

    @Test
    void 菜谱搜索_普通用户排除下架_我做过我收藏不排除() {
        Page<Dish> p = new Page<>(1, 10);
        p.setRecords(List.of());
        when(dishMapper.selectPage(any(), any(Wrapper.class))).thenReturn(p);
        ArgumentCaptor<Wrapper<Dish>> w = ArgumentCaptor.forClass(Wrapper.class);

        dishService.search(new DishSearchDTO());
        verify(dishMapper, times(1)).selectPage(any(), w.capture());
        assertThat(segment(w.getValue())).contains("offline_at IS NULL");

        // 「做过 / 收藏」列表要看到自己用过的（需要当前就餐成员）
        com.gudu.xsd.common.tenant.TenantContextHolder.set(
                new com.gudu.xsd.common.tenant.TenantContext(7L, 70L, 700L, com.gudu.xsd.common.tenant.TenantContext.MEMBER));
        try {
            DishSearchDTO done = new DishSearchDTO();
            done.setDone(true);
            dishService.search(done);
            verify(dishMapper, times(2)).selectPage(any(), w.capture());
            assertThat(segment(w.getValue())).doesNotContain("offline_at IS NULL");
        } finally {
            com.gudu.xsd.common.tenant.TenantContextHolder.clear();
        }
    }

    @Test
    void 菜谱列表_最新_热门_都排除下架() {
        when(dishMapper.selectList(any())).thenReturn(List.of());
        when(cookingRecordMapper.selectMaps(any())).thenReturn(List.of());
        ArgumentCaptor<Wrapper<Dish>> w = ArgumentCaptor.forClass(Wrapper.class);

        dishService.listBrowsable();
        dishService.latestDishes(5);
        dishService.hotDishes(5); // 无热门 -> 补位最新

        verify(dishMapper, times(3)).selectList(w.capture());
        w.getAllValues().forEach(x -> assertThat(segment(x)).contains("offline_at IS NULL"));
        ArgumentCaptor<Wrapper> rec = ArgumentCaptor.forClass(Wrapper.class);
        verify(cookingRecordMapper).selectMaps(rec.capture());
        assertThat(segment(rec.getValue())).contains("offline_at IS NOT NULL");
    }

    @Test
    void 语义检索_命中的下架菜不返回() {
        DishVectorService vec = new DishVectorService(null, null, dishMapper, null, null, null, null, null);
        when(dishMapper.selectBatchIds(anyCollection())).thenReturn(
                List.of(dish(1L, "在售", null, false), dish(3L, "下架", null, true)));
        Document a = new Document("a", Map.of("dishId", 1L, "name", "在售"));
        Document b = new Document("b", Map.of("dishId", 3L, "name", "下架"));

        List<Document> out = vec.visibleOnly(List.of(a, b));

        assertThat(out).extracting(d -> ((Number) d.getMetadata().get("dishId")).longValue()).containsExactly(1L);
    }

    @Test
    void 照着做_复制到我家_下架公共菜谱被拒() {
        when(dishMapper.selectById(3L)).thenReturn(dish(3L, "下架", null, true));
        assertThatThrownBy(() -> dishService.fork(3L)).isInstanceOf(BizException.class).hasMessageContaining("已下架");
        verify(dishMapper, never()).insert(any(Dish.class));
    }

    // ------------------------------------------------------------------ 加入食集：新加拒绝，已有保留

    @Test
    void 守卫_requireNoneOffline() {
        DishVisibilityGuard guard = new DishVisibilityGuard(dishMapper);
        when(dishMapper.selectBatchIds(anyCollection())).thenReturn(
                List.of(dish(1L, "在售", null, false), dish(3L, "下架", null, true)));
        guard.requireNoneOffline(null);
        guard.requireNoneOffline(List.of());
        assertThatThrownBy(() -> guard.requireNoneOffline(List.of(1L, 3L))).hasMessageContaining("下架");
        when(dishMapper.selectBatchIds(anyCollection())).thenReturn(List.of(dish(1L, "在售", null, false)));
        guard.requireNoneOffline(List.of(1L));
    }

    private MenuService menuServiceWithOffline(MenuDishMapper menuDish, List<Long> inMenu) {
        DishVisibilityGuard guard = new DishVisibilityGuard(dishMapper);
        MenuService svc = spy(new MenuService(menuDish, dishMapper, null, null, null));
        svc.setDishGuard(guard);
        doReturn(new com.gudu.xsd.modules.menu.Menu()).when(svc).getById(5L);
        doReturn(true).when(svc).save(any());
        doReturn(true).when(svc).updateById(any());
        when(dishMapper.selectBatchIds(anyCollection())).thenAnswer(i -> ((java.util.Collection<?>) i.getArgument(0)).stream()
                .map(id -> (Long) id).map(id -> id == 3L ? dish(3L, "下架", null, true) : dish(id, "菜" + id, null, false))
                .toList());
        when(menuDish.selectList(any())).thenReturn(inMenu.stream().map(id -> {
            MenuDish md = new MenuDish();
            md.setDishId(id);
            return md;
        }).toList());
        return svc;
    }

    private static MenuSaveDTO saveDto(Long menuId, Long... dishIds) {
        MenuSaveDTO dto = new MenuSaveDTO();
        com.gudu.xsd.modules.menu.Menu m = new com.gudu.xsd.modules.menu.Menu();
        m.setId(menuId);
        m.setName("食集");
        dto.setMenu(m);
        dto.setDishes(java.util.Arrays.stream(dishIds).map(id -> {
            MenuDish md = new MenuDish();
            md.setDishId(id);
            return md;
        }).toList());
        return dto;
    }

    @Test
    void 食集_新建与追加_不能加下架菜() {
        MenuDishMapper menuDish = mock(MenuDishMapper.class);
        MenuService svc = menuServiceWithOffline(menuDish, List.of());

        assertThatThrownBy(() -> svc.saveWithDishes(saveDto(null, 1L, 3L))).hasMessageContaining("已下架");
        assertThatThrownBy(() -> svc.addDishes(5L, List.of(3L), 700L)).hasMessageContaining("已下架");
        verify(menuDish, never()).insert(any(MenuDish.class));

        assertThat(svc.addDishes(5L, List.of(1L), 700L)).containsExactly(1L);
    }

    @Test
    void 食集_已有的下架菜照常保留_整单保存不被拦() {
        MenuDishMapper menuDish = mock(MenuDishMapper.class);
        MenuService svc = menuServiceWithOffline(menuDish, List.of(3L)); // 3 号已在食集里，后来被下架

        svc.saveWithDishes(saveDto(5L, 3L, 1L)); // 更新食集：3 号保留、新增 1 号
        verify(menuDish, times(2)).insert(any(MenuDish.class));
    }

    @Test
    void 按已有餐程或历史食集生成_允许含下架菜() {
        MenuDishMapper menuDish = mock(MenuDishMapper.class);
        MenuService svc = menuServiceWithOffline(menuDish, List.of());

        svc.saveWithDishesKeepingOffline(saveDto(null, 3L));

        verify(menuDish).insert(any(MenuDish.class));
    }

    // ------------------------------------------------------------------ 详情仍可读并带标记

    @Test
    void 详情_下架公共菜谱仍返回数据_带offline标记() throws Exception {
        when(dishMapper.selectById(3L)).thenReturn(dish(3L, "下架", null, true));

        DishService.DishDetail d = dishService.detail(3L);

        assertThat(d.dish().isOffline()).isTrue();
        String json = new com.fasterxml.jackson.databind.ObjectMapper()
                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                .writeValueAsString(d.dish());
        assertThat(json).contains("\"offline\":true").contains("offlineAt").doesNotContain("offlineReason");
    }

    @Test
    void 客户端不能通过保存接口改下架状态() throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
        om.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        Dish d = om.readValue("{\"name\":\"x\",\"offlineAt\":\"2026-11-01T10:00:00\",\"offlineReason\":\"r\",\"offline\":true}",
                Dish.class);
        assertThat(d.getOfflineAt()).isNull();
        assertThat(d.getOfflineReason()).isNull();
        assertThat(d.isOffline()).isFalse();
    }
}
