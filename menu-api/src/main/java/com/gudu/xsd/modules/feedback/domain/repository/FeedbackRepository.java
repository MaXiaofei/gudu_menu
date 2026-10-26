package com.gudu.xsd.modules.feedback.domain.repository;

import com.gudu.xsd.modules.feedback.domain.model.Feedback;
import com.gudu.xsd.modules.feedback.domain.model.FeedbackPage;
import com.gudu.xsd.modules.feedback.domain.model.FeedbackStatus;
import com.gudu.xsd.modules.feedback.domain.model.FeedbackType;

import java.time.LocalDateTime;
import java.util.Optional;

public interface FeedbackRepository {

    void save(Feedback feedback);

    Optional<Feedback> findById(Long id);

    long countByMemberSince(Long memberId, LocalDateTime since);

    /** type / status 为 null 不过滤；按创建时间倒序。 */
    FeedbackPage page(FeedbackType type, FeedbackStatus status, int pageNum, int pageSize);

    /** 某账号自己提交的反馈，按创建时间倒序。 */
    FeedbackPage pageByMember(Long memberId, int pageNum, int pageSize);

    /** 账号合并（AccountMerged）：把归属 {@code from} 账号的记录改指 {@code to}。返回改动条数。 */
    int reassignAccount(Long from, Long to);
}
