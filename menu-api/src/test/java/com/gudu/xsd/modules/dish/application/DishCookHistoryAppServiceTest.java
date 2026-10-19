package com.gudu.xsd.modules.dish.application;

import com.gudu.xsd.modules.cookbook.application.CookingRecordAppService;
import com.gudu.xsd.modules.cookbook.domain.model.LastCooked;
import com.gudu.xsd.modules.dish.Dish;
import com.gudu.xsd.modules.member.Member;
import com.gudu.xsd.modules.member.MemberService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DishCookHistoryAppServiceTest {

    CookingRecordAppService records = mock(CookingRecordAppService.class);
    MemberService members = mock(MemberService.class);
    DishCookHistoryAppService svc = new DishCookHistoryAppService(records, members);

    private static Dish dish(long id) {
        Dish d = new Dish();
        d.setId(id);
        return d;
    }

    @Test
    void 回填最近一次时间与做菜人_无记录保持null() {
        LocalDateTime at = LocalDateTime.of(2026, 10, 13, 18, 30);
        when(records.lastCooked(List.of(1L, 2L))).thenReturn(Map.of(1L, new LastCooked(1L, 7L, at)));
        Member mama = new Member();
        mama.setId(7L);
        mama.setName("妈妈");
        mama.setAvatarUrl("/gudu/uploads/a.png");
        when(members.listByIds(List.of(7L))).thenReturn(List.of(mama));
        Dish d1 = dish(1), d2 = dish(2);

        svc.fillLastCooked(List.of(d1, d2));

        assertThat(d1.getLastCookedAt()).isEqualTo(at);
        assertThat(d1.getLastCookedBy().name()).isEqualTo("妈妈");
        assertThat(d1.getLastCookedBy().avatarUrl()).isEqualTo("/gudu/uploads/a.png");
        assertThat(d2.getLastCookedAt()).isNull();
        assertThat(d2.getLastCookedBy()).isNull();
    }

    @Test
    void 无记录不查成员() {
        when(records.lastCooked(any())).thenReturn(Map.of());
        svc.fillLastCooked(List.of(dish(1)));
        verify(members, never()).listByIds(any());
    }

    @Test
    void 成员已删_只填时间() {
        when(records.lastCooked(any())).thenReturn(Map.of(1L, new LastCooked(1L, 9L, LocalDateTime.of(2026, 10, 1, 8, 0))));
        when(members.listByIds(any())).thenReturn(List.of());
        Dish d = dish(1);
        svc.fillLastCooked(List.of(d));
        assertThat(d.getLastCookedAt()).isNotNull();
        assertThat(d.getLastCookedBy()).isNull();
    }
}
