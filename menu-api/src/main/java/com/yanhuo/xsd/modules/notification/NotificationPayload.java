package com.yanhuo.xsd.modules.notification;

/** 通知载荷（与通道实现解耦，业务侧只构造这个）。 */
public record NotificationPayload(Long memberId, String type, String title, String content) {}
