package com.gudu.xsd.modules.community.infrastructure;

import com.gudu.xsd.modules.community.domain.repository.AppealRepository;
import com.gudu.xsd.modules.community.infrastructure.mapper.CommunityAppealMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class MybatisAppealRepository implements AppealRepository {

    private final CommunityAppealMapper mapper;

    @Override
    public boolean add(Long postId, Long authorId, String text, LocalDateTime at) {
        return mapper.insertIgnore(postId, authorId, text, at) > 0;
    }

    @Override
    public Map<Long, Appeal> findByPosts(Collection<Long> postIds) {
        List<Long> ids = postIds == null ? List.of() : postIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return Map.of();
        return mapper.findByPosts(ids).stream().collect(Collectors.toMap(r -> r.getPostId(),
                r -> new Appeal(r.getPostId(), r.getReason(), r.getCreateTime(),
                        r.getTargetId() != null && r.getTargetId() == 1), (a, b) -> a));
    }

    @Override
    public void markHandled(Long postId, LocalDateTime at) {
        mapper.markHandled(postId, at);
    }

    @Override
    public long countPending() {
        return mapper.countPending();
    }
}
