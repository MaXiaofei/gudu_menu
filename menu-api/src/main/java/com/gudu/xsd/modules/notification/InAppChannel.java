package com.gudu.xsd.modules.notification;

import com.gudu.xsd.modules.notification.mapper.NotificationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InAppChannel implements NotificationChannel {

    private final NotificationMapper notificationMapper;

    @Override
    public String channelKey() { return "in_app"; }

    @Override
    public void send(NotificationPayload p) {
        Notification n = new Notification();
        n.setRecipientAccountId(p.recipient().accountId());
        n.setRecipientProfileId(p.recipient().profileId());
        n.setType(p.type());
        n.setChannel(channelKey());
        n.setTitle(p.title());
        n.setContent(p.content());
        n.setRefId(p.refId());
        n.setIsRead(0);
        notificationMapper.insert(n);
    }
}
