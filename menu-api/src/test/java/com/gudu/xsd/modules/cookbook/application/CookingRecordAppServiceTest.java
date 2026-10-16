package com.gudu.xsd.modules.cookbook.application;

import com.gudu.xsd.modules.cookbook.application.dto.RecentDishView;
import com.gudu.xsd.modules.cookbook.domain.model.CookedDishRecord;
import com.gudu.xsd.modules.cookbook.domain.model.DishCookStat;
import com.gudu.xsd.modules.cookbook.domain.repository.CookedDishRecordRepository;
import com.gudu.xsd.modules.dish.Dish;
import com.gudu.xsd.modules.dish.DishService;
import com.gudu.xsd.modules.menu.domain.event.DishCookedEvent;
import com.gudu.xsd.modules.menu.domain.model.CookSource;
import com.gudu.xsd.modules.menu.domain.model.CookedDish;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 做菜记录应用服务：事件落库（幂等）、最近做过、你常做、次数。 */
class CookingRecordAppServiceTest {

    static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    CookedDishRecordRepository repo;
    DishService dishService;
    CookingRecordAppService svc;

    @BeforeEach
    void setUp() {
        repo = mock(CookedDishRecordRepository.class);
        dishService = mock(DishService.class);
        svc = new CookingRecordAppService(repo, dishService, Clock.system(ZONE));
    }

    private static Dish dish(long id, String name) {
        Dish d = new Dish();
        d.setId(id);
        d.setName(name);
        d.setCoverUrl("/gudu/uploads/" + id + ".jpg");
        return d;
    }

    @Test
    void 事件落库_每道菜一条_带eventId与memo与source() {
        AtomicLong seq = new AtomicLong(100);
        doAnswer(inv -> {
            inv.getArgument(0, CookedDishRecord.class).identify(seq.incrementAndGet());
            return null;
        }).when(repo).save(any());
        DishCookedEvent e = new DishCookedEvent(7L, CookSource.MENU, 3L,
                List.of(new CookedDish(1L, BigDecimal.ONE), new CookedDish(2L, new BigDecimal("2"))),
                List.of(10L), List.of(20L), null);

        List<Long> ids = svc.recordCooked(e);

        assertThat(ids).containsExactly(101L, 102L);
        ArgumentCaptor<CookedDishRecord> cap = ArgumentCaptor.forClass(CookedDishRecord.class);
        verify(repo, times(2)).save(cap.capture());
        CookedDishRecord first = cap.getAllValues().get(0);
        assertThat(first.eventId()).isEqualTo(e.eventId());
        assertThat(first.source()).isEqualTo("menu");
        assertThat(first.menuId()).isEqualTo(3L);
        assertThat(first.memo()).isEqualTo("用完:10;用了一些:20");
        assertThat(first.cookedAt()).isEqualTo(LocalDateTime.ofInstant(e.occurredOn(), ZONE));
        assertThat(cap.getAllValues().get(1).servingFactor()).isEqualByComparingTo("2");
    }

    @Test
    void 事件重复投递_幂等跳过() {
        DishCookedEvent e = new DishCookedEvent(7L, CookSource.DISH, null, List.of(new CookedDish(1L, BigDecimal.ONE)),
                null, null, "n");
        when(repo.existsByEventId(e.eventId())).thenReturn(true);
        when(repo.idsByEventId(e.eventId())).thenReturn(List.of(5L));
        assertThat(svc.recordCooked(e)).containsExactly(5L);
        verify(repo, never()).save(any());
    }

    @Test
    void 监听器_转recordCooked() {
        CookingRecordAppService mocked = mock(CookingRecordAppService.class);
        DishCookedEvent e = new DishCookedEvent(7L, CookSource.DISH, null, List.of(), null, null, null);
        new DishCookedRecordListener(mocked).on(e);
        verify(mocked).recordCooked(e);
    }

    @Test
    void 最近做过_按菜去重_过滤已删菜_limit夹紧() {
        LocalDateTime t = LocalDateTime.of(2026, 10, 16, 19, 0);
        when(repo.recentByMember(eq(7L), anyInt())).thenReturn(List.of(
                new DishCookStat(2L, t, 3), new DishCookStat(1L, t.minusDays(1), 1), new DishCookStat(9L, t, 1)));
        when(dishService.listByIds(any())).thenReturn(List.of(dish(1, "番茄炒蛋"), dish(2, "可乐鸡翅")));

        List<RecentDishView> out = svc.recent(7L, 100);

        verify(repo).recentByMember(7L, CookingRecordAppService.MAX_RECENT);
        assertThat(out).extracting(RecentDishView::name).containsExactly("可乐鸡翅", "番茄炒蛋");
        assertThat(out.get(0).cookedCount()).isEqualTo(3);
        assertThat(out.get(0).lastCookedAt()).isEqualTo(t);
        assertThat(out.get(0).coverImage()).isEqualTo("/gudu/uploads/2.jpg");
        svc.recent(7L, 0);
        verify(repo).recentByMember(7L, 1);
    }

    @Test
    void 最近做过_无记录不查菜() {
        when(repo.recentByMember(eq(7L), anyInt())).thenReturn(List.of());
        assertThat(svc.recent(7L, 10)).isEmpty();
        verify(dishService, never()).listByIds(any());
    }

    @Test
    void 委托仓储_常做与次数与记录id() {
        when(repo.mostCookedByMember(7L, 6)).thenReturn(List.of(new DishCookStat(1L, null, 4)));
        when(repo.countsByMember(7L, List.of(1L))).thenReturn(Map.of(1L, 4));
        when(repo.idsByEventId("e")).thenReturn(List.of(1L));
        assertThat(svc.mostCooked(7L, 6)).hasSize(1);
        assertThat(svc.cookedCounts(7L, List.of(1L))).containsEntry(1L, 4);
        assertThat(svc.recordIdsOf("e")).containsExactly(1L);
    }
}
