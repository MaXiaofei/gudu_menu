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

    /**
     * 我的消息 = 发给「本账号」的 + 发给「本人当前就餐档案」的。两列分开比对（账号 id 只对 recipient_account_id，
     * 档案 id 只对 recipient_profile_id），即使某账号 id 与另一人的档案 id 数值相同也不会串号。
     *
     * @param accountId 登录账号 id（必填）
     * @param profileId 当前就餐档案 id（可空：无档案时只看账号级消息）
     */
    public List<Notification> list(Long accountId, Long profileId) {
        return notificationMapper.selectList(
            mine(new QueryWrapper<Notification>(), accountId, profileId).orderByDesc("create_time"));
    }

    public long unreadCount(Long accountId, Long profileId) {
        return notificationMapper.selectCount(
            mine(new QueryWrapper<Notification>(), accountId, profileId).eq("is_read", 0));
    }

    /** 标记已读：只能标记「我的」消息（带收件人条件防越权）。 */
    public void markRead(Long id, Long accountId, Long profileId) {
        notificationMapper.update(null,
            mine(new UpdateWrapper<Notification>(), accountId, profileId).eq("id", id).set("is_read", 1));
    }

    /**
     * 账号注销：删除发给该账号的通知，以及发给其<b>本人名下档案</b>的通知。
     * 两列分开删，绝不拿账号 id 去比档案列（撞号会误删他人通知）。调用方需以系统模式运行。
     */
    public int deleteForAccount(Long accountId, Collection<Long> ownProfileIds) {
        int n = 0;
        if (accountId != null) {
            n += notificationMapper.delete(new QueryWrapper<Notification>().eq("recipient_account_id", accountId));
        }
        if (ownProfileIds != null && !ownProfileIds.isEmpty()) {
            n += notificationMapper.delete(new QueryWrapper<Notification>().in("recipient_profile_id", ownProfileIds));
        }
        return n;
    }

    /** 账号合并：账号级通知 W → P（档案级通知随档案，不动）。返回改指条数。 */
    public int reassignAccount(Long from, Long to) {
        if (from == null || to == null || from.equals(to)) return 0;
        return notificationMapper.update(null, new UpdateWrapper<Notification>()
            .eq("recipient_account_id", from).set("recipient_account_id", to));
    }

    private static <W extends com.baomidou.mybatisplus.core.conditions.AbstractWrapper<Notification, String, W>> W mine(
            W w, Long accountId, Long profileId) {
        if (accountId == null && profileId == null) {
            throw new IllegalArgumentException("查询通知必须带账号或档案");
        }
        return w.and(x -> {
            if (accountId != null) x.eq("recipient_account_id", accountId);
            if (profileId != null) {
                if (accountId != null) x.or();
                x.eq("recipient_profile_id", profileId);
            }
        });
    }
}
