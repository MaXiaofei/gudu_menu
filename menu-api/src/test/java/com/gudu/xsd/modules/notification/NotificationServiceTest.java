package com.gudu.xsd.modules.notification;

import com.gudu.xsd.modules.notification.mapper.NotificationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * 通知服务单元测试：mock NotificationMapper + 假 NotificationChannel。
 * 覆盖 send（通道分发 + 未注册通道静默）、sendAll（多通道）、
 * 收件人查询 / 已读 / 注销 / 合并 的真实 SQL 语义见 NotificationRecipientIsolationTest（H2）。
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
        return new NotificationPayload(NotificationRecipient.profile(1L), "expire", "临期提醒", "牛奶明天到期");
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
}
