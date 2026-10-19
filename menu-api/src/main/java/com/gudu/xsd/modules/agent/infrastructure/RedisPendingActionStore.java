package com.gudu.xsd.modules.agent.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gudu.xsd.modules.agent.domain.model.ActionStatus;
import com.gudu.xsd.modules.agent.domain.model.PendingAction;
import com.gudu.xsd.modules.agent.domain.model.UndoTicket;
import com.gudu.xsd.modules.agent.domain.repository.PendingActionStore;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Redis 实现：动作正文与状态分两个键（状态用 Lua 比较并设置，保证并发确认只成功一次）；
 * 会话 → 动作 id 列表；撤销凭据单独键、取出即删。键保留 {@link #KEEP}（大于 5 分钟有效期，便于展示「已过期」）。
 */
@Repository
@RequiredArgsConstructor
public class RedisPendingActionStore implements PendingActionStore {

    static final Duration KEEP = Duration.ofMinutes(30);
    private static final String BODY = "agent:action:";
    private static final String STATUS = "agent:action-status:";
    private static final String SESSION = "agent:session-actions:";
    private static final String UNDO = "agent:undo:";

    private static final DefaultRedisScript<Long> CAS = new DefaultRedisScript<>(
            "local v = redis.call('GET', KEYS[1]); if v == ARGV[1] then redis.call('SET', KEYS[1], ARGV[2], 'KEEPTTL'); "
                    + "return 1 end; return 0", Long.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    @Override
    public void save(PendingAction a) {
        redis.opsForValue().set(BODY + a.actionId(), write(a), KEEP);
        redis.opsForValue().set(STATUS + a.actionId(), a.status().name(), KEEP);
        if (a.sessionId() != null) {
            String key = SESSION + a.sessionId();
            List<String> ids = redis.opsForList().range(key, 0, -1);
            if (ids == null || !ids.contains(a.actionId())) redis.opsForList().rightPush(key, a.actionId());
            redis.expire(key, KEEP);
        }
    }

    @Override
    public Optional<PendingAction> find(String actionId) {
        String body = redis.opsForValue().get(BODY + actionId);
        if (body == null) return Optional.empty();
        PendingAction a = read(body, PendingAction.class);
        if (a == null) return Optional.empty();
        String status = redis.opsForValue().get(STATUS + actionId);
        return Optional.of(status == null ? a : a.withStatus(ActionStatus.valueOf(status)));
    }

    @Override
    public boolean transition(String actionId, ActionStatus from, ActionStatus to) {
        Long r = redis.execute(CAS, List.of(STATUS + actionId), from.name(), to.name());
        return r != null && r == 1L;
    }

    @Override
    public List<PendingAction> listBySession(String sessionId) {
        List<String> ids = redis.opsForList().range(SESSION + sessionId, 0, -1);
        List<PendingAction> out = new ArrayList<>();
        if (ids != null) for (String id : ids) find(id).ifPresent(out::add);
        return out;
    }

    @Override
    public void saveUndo(UndoTicket t) {
        redis.opsForValue().set(UNDO + t.token(), write(t), UndoTicket.TTL.plusSeconds(30));
    }

    @Override
    public Optional<UndoTicket> takeUndo(String token) {
        String v = redis.opsForValue().getAndDelete(UNDO + token);
        return Optional.ofNullable(v == null ? null : read(v, UndoTicket.class));
    }

    private String write(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            throw new IllegalStateException("动作序列化失败", e);
        }
    }

    private <T> T read(String s, Class<T> type) {
        try {
            return objectMapper.readValue(s, type);
        } catch (Exception e) {
            return null;
        }
    }
}
