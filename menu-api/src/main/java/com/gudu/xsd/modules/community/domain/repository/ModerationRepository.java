package com.gudu.xsd.modules.community.domain.repository;

import com.gudu.xsd.modules.community.domain.model.ModerationAction;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 审核审计日志与发帖封禁。 */
public interface ModerationRepository {

    void log(Long postId, Long operatorId, ModerationAction action, String reason, String note, LocalDateTime at);

    void ban(Long userId, LocalDateTime until, String reason);

    Optional<LocalDateTime> bannedUntil(Long userId);

    /** 每帖最近一次处置。 */
    Map<Long, LogEntry> latestOf(Collection<Long> postIds);

    /** 作者被封禁过几次（BAN_7D 处置次数）。 */
    long banCount(Long authorId);

    /** 某帖的处置记录（时间倒序）。 */
    List<LogEntry> logsOf(Long postId);

    /** since 之后人工处置的平均耗时（处置时间 − 发帖时间，分钟）；无记录为空。 */
    Optional<Double> avgHandleMinutesSince(LocalDateTime since);

    record LogEntry(Long operatorId, ModerationAction action, String reason, String note, LocalDateTime at) {
    }
}
