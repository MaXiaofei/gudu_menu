package com.gudu.xsd.modules.community.domain.repository;

import com.gudu.xsd.modules.community.domain.model.Topic;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 话题与挑战打卡。 */
public interface TopicRepository {

    Optional<Topic> findById(Long id);

    List<Topic> findByIds(Collection<Long> ids);

    /** 全部话题按 sort 升序。 */
    List<Topic> all();

    /** 打卡（每人每话题每天一次，唯一键幂等）。 */
    void checkin(Long topicId, Long userId, LocalDate date, Long postId);

    List<Checkin> checkins(Long topicId, LocalDate from, LocalDate to);

    long participantCount(Long topicId);

    record Checkin(Long userId, LocalDate date, Long postId) {
    }
}
