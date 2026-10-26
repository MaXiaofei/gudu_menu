package com.gudu.xsd.modules.notification;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.gudu.xsd.modules.notification.mapper.NotificationMapper;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class NotificationService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(NotificationService.class);

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
        if (ch == null) { log.warn("通知通道未注册: {}", channelKey); return; }
        ch.send(payload);
    }

    public void sendAll(NotificationPayload payload, Collection<String> channelKeys) {
        channelKeys.forEach(k -> send(payload, k));
    }

    public List<Notification> list(Long memberId) {
        return list(List.of(memberId));
    }

    /**
     * 多个收件人的消息合并（当前就餐成员 + 登录账号：反馈回复等账号级消息发给登录账号，
     * 切换到家人档案时也要能看到）。
     */
    public List<Notification> list(Collection<Long> memberIds) {
        return notificationMapper.selectList(
            new QueryWrapper<Notification>().in("member_id", memberIds).orderByDesc("create_time"));
    }

    public long unreadCount(Long memberId) {
        return unreadCount(List.of(memberId));
    }

    public long unreadCount(Collection<Long> memberIds) {
        return notificationMapper.selectCount(new QueryWrapper<Notification>()
            .in("member_id", memberIds).eq("is_read", 0));
    }

    /** 账号注销：删除发给该账号的全部通知，返回删除条数（调用方需以系统模式运行）。 */
    public int deleteByMember(Long memberId) {
        if (memberId == null) return 0;
        return notificationMapper.delete(new QueryWrapper<Notification>().eq("member_id", memberId));
    }

    public void markRead(Long id, Long memberId) {
        markRead(id, List.of(memberId));
    }

    public void markRead(Long id, Collection<Long> memberIds) {
        notificationMapper.update(null, new UpdateWrapper<Notification>()
            .eq("id", id).in("member_id", memberIds).set("is_read", 1));
    }
}
