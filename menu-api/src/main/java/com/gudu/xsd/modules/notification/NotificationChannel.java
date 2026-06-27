package com.gudu.xsd.modules.notification;

/** 通知通道策略。新增通道（如邮件/短信）只需实现并注册为 Bean。 */
public interface NotificationChannel {
    /** 通道标识，业务侧用这个 key 选择通道。 */
    String channelKey();
    /** 实际投递。 */
    void send(NotificationPayload payload);
}
