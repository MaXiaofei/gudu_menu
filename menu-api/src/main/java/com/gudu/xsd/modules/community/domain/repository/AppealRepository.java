package com.gudu.xsd.modules.community.domain.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;

/** 作者申诉（每帖一次，主键保证）。 */
public interface AppealRepository {

    int MAX_TEXT = 300;

    /** 新增申诉；该帖已申诉过返回 false。 */
    boolean add(Long postId, Long authorId, String text, LocalDateTime at);

    Map<Long, Appeal> findByPosts(Collection<Long> postIds);

    /** 运营重新处置后关闭申诉。 */
    void markHandled(Long postId, LocalDateTime at);

    long countPending();

    record Appeal(Long postId, String text, LocalDateTime createdAt, boolean handled) {
    }
}
