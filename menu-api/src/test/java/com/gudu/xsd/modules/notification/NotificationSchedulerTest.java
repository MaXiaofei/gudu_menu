package com.gudu.xsd.modules.notification;

import com.gudu.xsd.modules.member.Member;
import com.gudu.xsd.modules.member.mapper.MemberMapper;
import com.gudu.xsd.modules.pantry.PantryService;
import com.gudu.xsd.modules.pantry.PantryVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * NotificationScheduler.checkExpiring 逻辑测试（Mockito）。
 * 不依赖 Spring；mock PantryService / NotificationService / MemberMapper。
 *
 * 契约：
 *  - 临期列表非空 + 有掌勺成员 → 对每条临期发一条 in_app 通知，返回临期数；
 *  - 空列表 → 不发通知，返回 0；
 *  - 无掌勺成员（selectOne 返回 null）→ 不发通知，返回 0。
 */
@ExtendWith(MockitoExtension.class)
class NotificationSchedulerTest {

    @Mock
    private PantryService pantryService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private MemberMapper memberMapper;

    @InjectMocks
    private NotificationScheduler scheduler;

    @Test
    void 有临期食材_且有掌勺成员_逐条发送通知_返回临期数() {
        // 两条临期库存
        PantryVO tomato = new PantryVO();
        tomato.setId(1L);
        tomato.setIngredientId(10L);
        tomato.setIngredientName("番茄");
        PantryVO egg = new PantryVO();
        egg.setId(2L);
        egg.setIngredientId(11L);
        egg.setIngredientName("鸡蛋");
        when(pantryService.listExpiring(3)).thenReturn(List.of(tomato, egg));

        // 掌勺成员
        Member chef = new Member();
        chef.setId(99L);
        when(memberMapper.selectOne(any())).thenReturn(chef);

        int sent = scheduler.checkExpiring(3);

        assertThat(sent).isEqualTo(2);
        // 每条临期发一次 in_app
        verify(notificationService, times(2)).send(any(NotificationPayload.class), eq("in_app"));
        // 内容里含食材名 + 天数（抽一个 payload 校验）
        verify(notificationService).send(argThat((NotificationPayload p) ->
                p != null && p.memberId() == 99L
                        && "expiry".equals(p.type())
                        && p.content().contains("番茄")
                        && p.content().contains("3")), eq("in_app"));
    }

    @Test
    void 无临期食材_不发通知_返回0() {
        when(pantryService.listExpiring(anyInt())).thenReturn(List.of());

        int sent = scheduler.checkExpiring(3);

        assertThat(sent).isZero();
        verifyNoInteractions(notificationService);
        verifyNoInteractions(memberMapper);
    }

    @Test
    void 无掌勺成员_不发通知_返回0() {
        PantryVO p = new PantryVO();
        p.setIngredientId(10L);
        p.setIngredientName("番茄");
        when(pantryService.listExpiring(3)).thenReturn(List.of(p));
        when(memberMapper.selectOne(any())).thenReturn(null); // 无掌勺

        int sent = scheduler.checkExpiring(3);

        assertThat(sent).isZero();
        verify(notificationService, never()).send(any(), any());
    }

    @Test
    void 食材名缺失时回退到食材ID() {
        PantryVO p = new PantryVO();
        p.setIngredientId(42L);
        p.setIngredientName(null); // 名字缺失
        when(pantryService.listExpiring(5)).thenReturn(List.of(p));
        Member chef = new Member();
        chef.setId(7L);
        when(memberMapper.selectOne(any())).thenReturn(chef);

        int sent = scheduler.checkExpiring(5);

        assertThat(sent).isEqualTo(1);
        verify(notificationService).send(argThat((NotificationPayload payload) ->
                payload != null && payload.content().contains("#42")), eq("in_app"));
    }
}
