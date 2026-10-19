package com.gudu.xsd.modules.community.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gudu.xsd.modules.community.domain.model.ModerationAction;
import com.gudu.xsd.modules.community.domain.repository.ModerationRepository;
import com.gudu.xsd.modules.community.infrastructure.mapper.CommunityModerationLogMapper;
import com.gudu.xsd.modules.community.infrastructure.mapper.CommunityRelationMapper;
import com.gudu.xsd.modules.community.infrastructure.po.CommunityModerationLogPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class MybatisModerationRepository implements ModerationRepository {

    private final CommunityModerationLogMapper logMapper;
    private final CommunityRelationMapper relationMapper;

    @Override
    public void log(Long postId, Long operatorId, ModerationAction action, String reason, String note,
                    LocalDateTime at) {
        CommunityModerationLogPO po = new CommunityModerationLogPO();
        po.setPostId(postId);
        po.setOperatorId(operatorId);
        po.setAction(action.name());
        po.setReason(reason);
        po.setNote(note);
        po.setCreateTime(at);
        logMapper.insert(po);
    }

    @Override
    public void ban(Long userId, LocalDateTime until, String reason) {
        relationMapper.upsertBan(userId, until, reason);
    }

    @Override
    public Optional<LocalDateTime> bannedUntil(Long userId) {
        return Optional.ofNullable(relationMapper.bannedUntil(userId));
    }

    @Override
    public List<LogEntry> logsOf(Long postId) {
        return logMapper.selectList(new QueryWrapper<CommunityModerationLogPO>().eq("post_id", postId)
                        .orderByDesc("create_time").orderByDesc("id"))
                .stream().map(l -> new LogEntry(l.getOperatorId(), ModerationAction.of(l.getAction()), l.getReason(),
                        l.getNote(), l.getCreateTime())).toList();
    }

    @Override
    public Optional<Double> avgHandleMinutesSince(LocalDateTime since) {
        return Optional.ofNullable(logMapper.avgHandleMinutesSince(since));
    }

    @Override
    public Map<Long, LogEntry> latestOf(Collection<Long> postIds) {
        List<Long> ids = postIds == null ? List.of() : postIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return Map.of();
        Map<Long, LogEntry> out = new java.util.HashMap<>();
        for (CommunityModerationLogPO l : logMapper.selectList(new QueryWrapper<CommunityModerationLogPO>()
                .in("post_id", ids).orderByDesc("create_time").orderByDesc("id"))) {
            out.putIfAbsent(l.getPostId(), new LogEntry(l.getOperatorId(), ModerationAction.of(l.getAction()),
                    l.getReason(), l.getNote(), l.getCreateTime()));
        }
        return out;
    }

    @Override
    public long banCount(Long authorId) {
        return logMapper.banCount(authorId);
    }
}
