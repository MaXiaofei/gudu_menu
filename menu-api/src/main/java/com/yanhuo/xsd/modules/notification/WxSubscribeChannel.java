package com.yanhuo.xsd.modules.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 微信小程序订阅消息通道。
 * 【V1 留入口】当前不投递：未办微信 appid + 订阅消息模板审核。
 * 微信资质到位后，在此实现：
 *   1. 小程序端 wx.requestSubscribeMessage 拿用户一次性授权
 *   2. 本端调微信 OpenAPI subscribeMessage.send（需 appid/secret/access_token）
 * 业务侧无需改动 —— 仍调 NotificationService.send(payload, "wx_subscribe")。
 */
@Slf4j
@Component
public class WxSubscribeChannel implements NotificationChannel {

    @Override
    public String channelKey() { return "wx_subscribe"; }

    @Override
    public void send(NotificationPayload p) {
        log.debug("[wx_subscribe] 通道未启用（V1 预留），丢弃通知：type={} memberId={}", p.type(), p.memberId());
    }
}
