package com.gudu.xsd.modules.feedback.domain.repository;

import com.gudu.xsd.modules.feedback.domain.model.Feedback;
import com.gudu.xsd.modules.feedback.domain.model.FeedbackPage;
import com.gudu.xsd.modules.feedback.domain.model.FeedbackStatus;

import java.time.LocalDateTime;
import java.util.Optional;

public interface FeedbackRepository {

    void save(Feedback feedback);

    Optional<Feedback> findById(Long id);

    long countByMemberSince(Long memberId, LocalDateTime since);

    /** status 为 null 不过滤；按创建时间倒序。 */
    FeedbackPage page(FeedbackStatus status, int pageNum, int pageSize);
}
