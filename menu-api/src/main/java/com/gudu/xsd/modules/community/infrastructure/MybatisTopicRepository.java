package com.gudu.xsd.modules.community.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gudu.xsd.modules.community.domain.model.Topic;
import com.gudu.xsd.modules.community.domain.repository.TopicRepository;
import com.gudu.xsd.modules.community.infrastructure.mapper.CommunityTopicCheckinMapper;
import com.gudu.xsd.modules.community.infrastructure.mapper.CommunityTopicMapper;
import com.gudu.xsd.modules.community.infrastructure.po.CommunityTopicCheckinPO;
import com.gudu.xsd.modules.community.infrastructure.po.CommunityTopicPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class MybatisTopicRepository implements TopicRepository {

    private final CommunityTopicMapper mapper;
    private final CommunityTopicCheckinMapper checkinMapper;

    @Override
    public Optional<Topic> findById(Long id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(mapper.selectById(id)).map(MybatisTopicRepository::toDomain);
    }

    @Override
    public List<Topic> findByIds(Collection<Long> ids) {
        List<Long> list = ids == null ? List.of() : ids.stream().filter(Objects::nonNull).distinct().toList();
        if (list.isEmpty()) return List.of();
        return mapper.selectBatchIds(list).stream().map(MybatisTopicRepository::toDomain).toList();
    }

    @Override
    public List<Topic> all() {
        return mapper.selectList(new QueryWrapper<CommunityTopicPO>().orderByAsc("sort").orderByAsc("id"))
                .stream().map(MybatisTopicRepository::toDomain).toList();
    }

    @Override
    public void checkin(Long topicId, Long userId, LocalDate date, Long postId) {
        checkinMapper.insertIgnore(topicId, userId, date, postId);
    }

    @Override
    public List<Checkin> checkins(Long topicId, LocalDate from, LocalDate to) {
        return checkinMapper.selectList(new QueryWrapper<CommunityTopicCheckinPO>().eq("topic_id", topicId)
                        .between("checkin_date", from, to))
                .stream().map(c -> new Checkin(c.getUserId(), c.getCheckinDate(), c.getPostId())).toList();
    }

    @Override
    public long participantCount(Long topicId) {
        return checkinMapper.participantCount(topicId);
    }

    static Topic toDomain(CommunityTopicPO po) {
        return new Topic(po.getId(), po.getName(), po.getIntro(), po.getRule(),
                po.getChallenge() != null && po.getChallenge() == 1, po.getStartDate(), po.getEndDate(),
                po.getSort() == null ? 0 : po.getSort());
    }
}
