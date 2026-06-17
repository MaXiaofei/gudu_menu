package com.yanhuo.xsd.modules.notification;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.yanhuo.xsd.modules.notification.mapper.NotificationMapper;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class NotificationService {

    private final Map<String, NotificationChannel> channels;
    private final NotificationMapper notificationMapper;

    /** Spring 自动注入所有 NotificationChannel Bean，按 channelKey 索引。 */
    public NotificationService(List<NotificationChannel> channelList, NotificationMapper notificationMapper) {
        this.channels = channelList.stream()
            .collect(Collectors.toMap(NotificationChannel::channelKey, Function.identity()));
        this.notificationMapper = notificationMapper;
    }

    public void send(NotificationPayload payload, String channelKey) {
        NotificationChannel ch = channels.get(channelKey);
        if (ch != null) ch.send(payload);
    }

    public void sendAll(NotificationPayload payload, Collection<String> channelKeys) {
        channelKeys.forEach(k -> send(payload, k));
    }

    public List<Notification> list(Long memberId) {
        return notificationMapper.selectList(
            new QueryWrapper<Notification>().eq("member_id", memberId).orderByDesc("create_time"));
    }

    public long unreadCount(Long memberId) {
        return notificationMapper.selectCount(new QueryWrapper<Notification>()
            .eq("member_id", memberId).eq("is_read", 0));
    }

    public void markRead(Long id) {
        notificationMapper.update(null, new UpdateWrapper<Notification>().eq("id", id).set("is_read", 1));
    }
}
