package com.gudu.xsd.modules.notification;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.gudu.xsd.modules.notification.mapper.NotificationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * 通知服务单元测试：mock NotificationMapper + 假 NotificationChannel。
 * 覆盖 send（通道分发 + 未注册通道静默）、sendAll（多通道）、
 * list/unreadCount（查询）、markRead（更新）。
 */
class NotificationServiceTest {

    private NotificationMapper mapper;
    private FakeChannel inApp;
    private FakeChannel wx;
    private NotificationService svc;

    /** 轻量假通道：记录收到的 payload，便于断言。 */
    static class FakeChannel implements NotificationChannel {
        final String key;
        final List<NotificationPayload> received = new java.util.ArrayList<>();
        FakeChannel(String key) { this.key = key; }
        @Override public String channelKey() { return key; }
        @Override public void send(NotificationPayload payload) { received.add(payload); }
    }

    @BeforeEach
    void setUp() {
        mapper = Mockito.mock(NotificationMapper.class);
        inApp = new FakeChannel("inApp");
        wx = new FakeChannel("wx");
        svc = new NotificationService(List.of(inApp, wx), mapper);
    }

    private NotificationPayload payload() {
        return new NotificationPayload(1L, "expire", "临期提醒", "牛奶明天到期");
    }

    // ---------------- send / sendAll ----------------

    /** send：按 channelKey 分发到对应通道。 */
    @Test
    void send_按通道key分发() {
        svc.send(payload(), "wx");

        assertThat(wx.received).hasSize(1);
        assertThat(wx.received.get(0).title()).isEqualTo("临期提醒");
        assertThat(inApp.received).isEmpty();
    }

    /** send：未注册的通道静默跳过（不抛异常，只记日志）。 */
    @Test
    void send_未注册通道_静默跳过() {
        svc.send(payload(), "sms"); // sms 未注册

        assertThat(inApp.received).isEmpty();
        assertThat(wx.received).isEmpty();
        // 不抛异常即通过
    }

    /** sendAll：遍历所有通道 key 分发。 */
    @Test
    void sendAll_多通道分发() {
        svc.sendAll(payload(), Set.of("inApp", "wx"));

        assertThat(inApp.received).hasSize(1);
        assertThat(wx.received).hasSize(1);
    }

    // ---------------- list ----------------

    /** list：按 memberId 查询通知。 */
    @Test
    void list_按memberId查询() {
        Notification n = new Notification();
        n.setId(1L);
        n.setMemberId(7L);
        n.setTitle("临期提醒");
        when(mapper.selectList(any())).thenReturn(List.of(n));

        var result = svc.list(7L);

        assertThat(result).extracting(Notification::getTitle).containsExactly("临期提醒");
    }

    // ---------------- unreadCount ----------------

    /** unreadCount：返回未读数。 */
    @Test
    void unreadCount_返回未读数() {
        // selectCount 返回 Long；用 doReturn 绕开泛型推断歧义
        Mockito.doReturn(Long.valueOf(3)).when(mapper).selectCount(any(Wrapper.class));

        long count = svc.unreadCount(7L);

        assertThat(count).isEqualTo(3L);
    }

    // ---------------- markRead ----------------

    /** markRead：调用 update 置 is_read=1，且带 memberId 条件防越权。 */
    @Test
    void markRead_调用update标记已读() {
        svc.markRead(100L, 7L);

        // 验证 update 被调用一次（UpdateWrapper 内部 SQL 不便断言，验证调用即可）
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<Notification>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).update(isNull(), captor.capture());
        assertThat(captor.getValue()).isNotNull();
    }
}
