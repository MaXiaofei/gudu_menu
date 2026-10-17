package com.gudu.xsd.modules.pantry.application;

import com.gudu.xsd.common.BizException;
import com.gudu.xsd.common.domain.DomainEvent;
import com.gudu.xsd.common.domain.DomainEventPublisher;
import com.gudu.xsd.common.domain.DomainException;
import com.gudu.xsd.modules.dict.DictService;
import com.gudu.xsd.modules.dict.SysDict;
import com.gudu.xsd.modules.menu.domain.event.DishCookedEvent;
import com.gudu.xsd.modules.menu.domain.model.CookSource;
import com.gudu.xsd.modules.nutrition.Ingredient;
import com.gudu.xsd.modules.nutrition.IngredientService;
import com.gudu.xsd.modules.pantry.application.dto.BestBeforeUpdate;
import com.gudu.xsd.modules.pantry.application.dto.ExpiringItemView;
import com.gudu.xsd.modules.pantry.domain.event.StockLevelChangedEvent;
import com.gudu.xsd.modules.pantry.domain.model.StockAction;
import com.gudu.xsd.modules.pantry.domain.model.StockItem;
import com.gudu.xsd.modules.pantry.domain.model.StockLevel;
import com.gudu.xsd.modules.pantry.domain.model.StockLogEntry;
import com.gudu.xsd.modules.pantry.domain.repository.StockItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** StockAppService（Mockito）：取聚合→行为→存→流水→发事件；临期/保质期建议。 */
class StockAppServiceTest {

    static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    static final LocalDate TODAY = LocalDate.of(2026, 10, 16);

    StockItemRepository repo;
    List<DomainEvent> published;
    IngredientService ingredientService;
    DictService dictService;
    StockAppService svc;

    @BeforeEach
    void setUp() {
        repo = mock(StockItemRepository.class);
        published = new ArrayList<>();
        DomainEventPublisher publisher = published::add;
        ingredientService = mock(IngredientService.class);
        dictService = mock(DictService.class);
        Clock clock = Clock.fixed(TODAY.atTime(12, 0).atZone(ZONE).toInstant(), ZONE);
        svc = new StockAppService(repo, publisher, ingredientService, dictService, clock);
    }

    private static Ingredient ing(long id, String name, Long cat) {
        Ingredient i = new Ingredient();
        i.setId(id);
        i.setName(name);
        i.setPurchaseCategoryId(cat);
        return i;
    }

    private StockLogEntry onlyLog() {
        ArgumentCaptor<StockLogEntry> cap = ArgumentCaptor.forClass(StockLogEntry.class);
        verify(repo).appendLog(cap.capture());
        return cap.getValue();
    }

    // ---------- adjustLevel ----------

    @Test
    void 设档位_没建档_新建并写manual流水_发事件() {
        when(repo.findByIngredientId(10L)).thenReturn(Optional.empty());

        svc.adjustLevel(10L, "ENOUGH", BestBeforeUpdate.set(TODAY.plusDays(3)), "朋友送");

        ArgumentCaptor<StockItem> saved = ArgumentCaptor.forClass(StockItem.class);
        verify(repo).save(saved.capture());
        assertThat(saved.getValue().bestBefore()).isEqualTo(TODAY.plusDays(3));
        StockLogEntry log = onlyLog();
        assertThat(log.action()).isEqualTo(StockAction.MANUAL);
        assertThat(log.before()).isNull();
        assertThat(log.after()).isEqualTo(StockLevel.ENOUGH);
        assertThat(log.note()).isEqualTo("朋友送");
        assertThat(published).singleElement().isInstanceOf(StockLevelChangedEvent.class);
    }

    @Test
    void 设档位_已建档_不传保质期则保留() {
        StockItem s = StockItem.rehydrate(1L, 10L, StockLevel.ENOUGH, TODAY.plusDays(5), null);
        when(repo.findByIngredientId(10L)).thenReturn(Optional.of(s));

        svc.adjustLevel(10L, "LOW", BestBeforeUpdate.keep(), null);

        assertThat(s.level()).isEqualTo(StockLevel.LOW);
        assertThat(s.bestBefore()).isEqualTo(TODAY.plusDays(5));
        assertThat(onlyLog().before()).isEqualTo(StockLevel.ENOUGH);
        assertThat(published).hasSize(1);
    }

    @Test
    void 设档位_传null清除保质期_指令为null按不改() {
        StockItem s = StockItem.rehydrate(1L, 10L, StockLevel.ENOUGH, TODAY.plusDays(5), null);
        when(repo.findByIngredientId(10L)).thenReturn(Optional.of(s));
        svc.adjustLevel(10L, "ENOUGH", BestBeforeUpdate.set(null), null);
        assertThat(s.bestBefore()).isNull();
        assertThat(published).isEmpty(); // 档位没变不发事件

        StockItem t = StockItem.rehydrate(2L, 11L, StockLevel.LOW, TODAY, null);
        when(repo.findByIngredientId(11L)).thenReturn(Optional.of(t));
        svc.adjustLevel(11L, "LOW", null, null);
        assertThat(t.bestBefore()).isEqualTo(TODAY);
    }

    @Test
    void 设档位_非法档位与空id拒绝() {
        assertThatThrownBy(() -> svc.adjustLevel(10L, "MANY", null, null)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> svc.adjustLevel(null, "LOW", null, null)).isInstanceOf(BizException.class);
        verify(repo, never()).save(any());
    }

    @Test
    void 改保质期_有档才改() {
        StockItem s = StockItem.rehydrate(1L, 10L, StockLevel.ENOUGH, null, null);
        when(repo.findByIngredientId(10L)).thenReturn(Optional.of(s));
        when(repo.findByIngredientId(11L)).thenReturn(Optional.empty());
        svc.changeBestBefore(10L, TODAY.plusDays(1));
        svc.changeBestBefore(11L, TODAY.plusDays(1));
        assertThat(s.bestBefore()).isEqualTo(TODAY.plusDays(1));
        verify(repo, times(1)).save(any());
    }

    // ---------- consume（DishCookedEvent 消费） ----------

    @Test
    void 做菜回写_用完变没有_用了一些降档_两边都勾以用完为准_批量加载一次() {
        StockItem tomato = StockItem.rehydrate(1L, 1L, StockLevel.LOW, TODAY.plusDays(1), null);
        StockItem egg = StockItem.rehydrate(2L, 2L, StockLevel.ENOUGH, null, null);
        when(repo.findByIngredientIds(any())).thenReturn(List.of(tomato, egg));

        svc.consume(List.of(1L, 3L), List.of(2L, 1L, 4L));

        verify(repo, times(1)).findByIngredientIds(any());
        assertThat(tomato.level()).isEqualTo(StockLevel.NONE);
        assertThat(tomato.bestBefore()).isNull();
        assertThat(egg.level()).isEqualTo(StockLevel.LOW);
        assertThat(egg.openedAt()).isEqualTo(TODAY.atTime(12, 0));
        ArgumentCaptor<StockLogEntry> logs = ArgumentCaptor.forClass(StockLogEntry.class);
        verify(repo, times(3)).appendLog(logs.capture());
        assertThat(logs.getAllValues()).extracting(StockLogEntry::ingredientId, StockLogEntry::action)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1L, StockAction.COOK),
                        org.assertj.core.groups.Tuple.tuple(3L, StockAction.COOK), // 没建档的「用完」建 NONE 档
                        org.assertj.core.groups.Tuple.tuple(2L, StockAction.COOK_PARTIAL));
        // 4L 没建档的「用了一些」不动
        verify(repo, times(3)).save(any());
        assertThat(published).hasSize(3); // 1:LOW→NONE, 3:新建 NONE, 2:ENOUGH→LOW
    }

    @Test
    void 做菜回写_用了一些但已是快用完_不写流水() {
        StockItem s = StockItem.rehydrate(1L, 1L, StockLevel.LOW, null, null);
        when(repo.findByIngredientIds(any())).thenReturn(List.of(s));
        svc.consume(null, List.of(1L));
        verify(repo, never()).appendLog(any());
        assertThat(published).isEmpty();
    }

    @Test
    void 做菜回写_全空_不查库() {
        svc.consume(List.of(), null);
        verify(repo, never()).findByIngredientIds(any());
    }

    @Test
    void 监听器_DishCookedEvent转consume() {
        StockAppService mocked = mock(StockAppService.class);
        DishCookedEvent e = new DishCookedEvent(1L, CookSource.DISH, null, List.of(), List.of(1L), List.of(2L), null);
        new DishCookedStockListener(mocked).on(e);
        verify(mocked).consume(List.of(1L), List.of(2L));
    }

    // ---------- expiring ----------

    @Test
    void 临期_含已过期_按剩余天数升序_过滤窗口外与已删食材() {
        when(repo.findAllWithBestBefore()).thenReturn(List.of(
                StockItem.rehydrate(1L, 1L, StockLevel.ENOUGH, TODAY.plusDays(2), null),
                StockItem.rehydrate(2L, 2L, StockLevel.LOW, TODAY.minusDays(1), null),
                StockItem.rehydrate(3L, 3L, StockLevel.ENOUGH, TODAY.plusDays(10), null),
                StockItem.rehydrate(4L, 4L, StockLevel.ENOUGH, TODAY, null)));
        when(ingredientService.listByIds(any())).thenReturn(List.of(ing(1, "牛奶", null), ing(2, "菠菜", null)));

        List<ExpiringItemView> out = svc.expiring(3);

        assertThat(out).extracting(ExpiringItemView::ingredientName).containsExactly("菠菜", "牛奶");
        assertThat(out.get(0).daysLeft()).isEqualTo(-1);
        assertThat(out.get(1).daysLeft()).isEqualTo(2);
        assertThat(out.get(1).level()).isEqualTo("ENOUGH");
    }

    @Test
    void 临期_无数据不查食材_days越界被夹紧() {
        when(repo.findAllWithBestBefore()).thenReturn(List.of(
                StockItem.rehydrate(1L, 1L, StockLevel.ENOUGH, TODAY.plusDays(400), null)));
        assertThat(svc.expiring(10_000)).isEmpty();
        assertThat(svc.expiring(-5)).isEmpty();
        verify(ingredientService, never()).listByIds(any());
    }

    @Test
    void 批量档位与保质期视图() {
        when(repo.findByIngredientIds(any())).thenReturn(List.of(
                StockItem.rehydrate(1L, 1L, StockLevel.LOW, TODAY.plusDays(1), null)));
        assertThat(svc.levelsOf(java.util.Arrays.asList(1L, null))).containsEntry(1L, StockLevel.LOW);
        assertThat(svc.shelfViewsOf(List.of(1L)).get(1L).daysLeft()).isEqualTo(1);
    }

    // ---------- shelf-life ----------

    @Test
    void 保质天数建议_字典配置优先_品类兜底_无品类为null() {
        when(ingredientService.getById(1L)).thenReturn(ing(1, "菠菜", 24L));
        SysDict veg = new SysDict();
        veg.setId(24L);
        veg.setName("蔬菜");
        when(dictService.getById(24L)).thenReturn(veg);
        assertThat(svc.suggestShelfLifeDays(1L)).isEqualTo(5);
        veg.setShelfLifeDays(4);
        assertThat(svc.suggestShelfLifeDays(1L)).isEqualTo(4);

        when(ingredientService.getById(2L)).thenReturn(ing(2, "神秘", null));
        assertThat(svc.suggestShelfLifeDays(2L)).isNull();
        when(ingredientService.getById(3L)).thenReturn(ing(3, "孤儿品类", 999L));
        assertThat(svc.suggestShelfLifeDays(3L)).isNull();
    }

    @Test
    void 保质天数建议_食材不存在或id为空抛异常() {
        assertThatThrownBy(() -> svc.suggestShelfLifeDays(null)).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> svc.suggestShelfLifeDays(99L)).hasMessageContaining("食材不存在");
    }

    // ---------- prototype-ui-rollout 0.7：余量统计 / 本月少扔 / 做菜流水保质期快照 ----------

    @Test
    void 余量统计_三档_临期_按品类降序_被删食材不计() {
        when(repo.findAll()).thenReturn(List.of(
                StockItem.rehydrate(1L, 1L, StockLevel.ENOUGH, TODAY.plusDays(2), null),
                StockItem.rehydrate(2L, 2L, StockLevel.LOW, null, null),
                StockItem.rehydrate(3L, 3L, StockLevel.NONE, null, null),
                StockItem.rehydrate(4L, 4L, StockLevel.ENOUGH, null, null),
                StockItem.rehydrate(5L, 99L, StockLevel.ENOUGH, null, null))); // 99 食材已删
        when(ingredientService.listByIds(any())).thenReturn(List.of(
                ing(1, "番茄", 10L), ing(2, "菠菜", 10L), ing(3, "鲈鱼", 20L), ing(4, "抽纸", null)));
        SysDict veg = new SysDict();
        veg.setId(10L);
        veg.setName("蔬菜");
        SysDict fish = new SysDict();
        fish.setId(20L);
        fish.setName("水产海鲜");
        when(dictService.listByGroup("purchase_category")).thenReturn(List.of(veg, fish));

        var v = svc.summary();

        assertThat(v.enough()).isEqualTo(2);
        assertThat(v.low()).isEqualTo(1);
        assertThat(v.out()).isEqualTo(1);
        assertThat(v.expiring3d()).isEqualTo(1);
        assertThat(v.byCategory()).extracting(c -> c.name() + ":" + c.count())
                .containsExactly("蔬菜:2", "水产海鲜:1", "其他:1");
    }

    @Test
    void 本月少扔_按自然月区间查询() {
        when(repo.countCookedBeforeExpiry(any(), any(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(4L);

        assertThat(svc.savedFromWasteThisMonth()).isEqualTo(4L);
        verify(repo).countCookedBeforeExpiry(LocalDate.of(2026, 10, 1).atStartOfDay(),
                LocalDate.of(2026, 11, 1).atStartOfDay(), 3);
    }

    @Test
    void 做菜流水记下用掉前的保质期() {
        when(repo.findByIngredientIds(any())).thenReturn(List.of(
                StockItem.rehydrate(1L, 1L, StockLevel.ENOUGH, TODAY.plusDays(1), null),
                StockItem.rehydrate(2L, 2L, StockLevel.ENOUGH, TODAY.plusDays(5), null)));

        svc.consume(List.of(1L), List.of(2L));

        ArgumentCaptor<StockLogEntry> logs = ArgumentCaptor.forClass(StockLogEntry.class);
        verify(repo, times(2)).appendLog(logs.capture());
        assertThat(logs.getAllValues()).extracting(StockLogEntry::ingredientId, StockLogEntry::bestBefore)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(1L, TODAY.plusDays(1)),
                        org.assertj.core.groups.Tuple.tuple(2L, TODAY.plusDays(5)));
    }

    @Test
    void 临期项带插画key() {
        when(repo.findAllWithBestBefore()).thenReturn(List.of(
                StockItem.rehydrate(1L, 1L, StockLevel.ENOUGH, TODAY.plusDays(1), null)));
        when(ingredientService.listByIds(any())).thenReturn(List.of(ing(1, "番茄", 10L)));
        when(ingredientService.iconKeyMap(any())).thenReturn(java.util.Map.of(1L, "tomato"));

        assertThat(svc.expiring(3)).singleElement().extracting(ExpiringItemView::iconKey).isEqualTo("tomato");
    }
}
