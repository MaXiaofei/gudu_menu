package com.gudu.xsd.modules.community.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gudu.xsd.modules.community.domain.model.Comment;
import com.gudu.xsd.modules.community.domain.repository.CommentRepository;
import com.gudu.xsd.modules.community.infrastructure.mapper.CommunityCommentMapper;
import com.gudu.xsd.modules.community.infrastructure.po.CommunityCommentPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class MybatisCommentRepository implements CommentRepository {

    private final CommunityCommentMapper mapper;

    @Override
    public void save(Comment c) {
        CommunityCommentPO po = new CommunityCommentPO();
        po.setPostId(c.postId());
        po.setAuthorId(c.authorId());
        po.setParentId(c.parentId());
        po.setText(c.text());
        po.setLikeCount(0);
        po.setCreateTime(c.createTime());
        mapper.insert(po);
        c.identify(po.getId());
    }

    @Override
    public Optional<Comment> findById(Long id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(mapper.selectById(id)).map(MybatisCommentRepository::toDomain);
    }

    @Override
    public List<Comment> topLevel(Long postId, Long afterId, int limit) {
        return mapper.selectList(new QueryWrapper<CommunityCommentPO>().eq("post_id", postId).isNull("parent_id")
                        .gt(afterId != null, "id", afterId).orderByAsc("id").last("LIMIT " + limit))
                .stream().map(MybatisCommentRepository::toDomain).toList();
    }

    @Override
    public Map<Long, List<Comment>> replies(Collection<Long> parentIds) {
        List<Long> ids = parentIds == null ? List.of() : parentIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return Map.of();
        return mapper.selectList(new QueryWrapper<CommunityCommentPO>().in("parent_id", ids).orderByAsc("id"))
                .stream().map(MybatisCommentRepository::toDomain)
                .collect(Collectors.groupingBy(Comment::parentId));
    }

    static Comment toDomain(CommunityCommentPO po) {
        return Comment.rehydrate(po.getId(), po.getPostId(), po.getAuthorId(), po.getParentId(), po.getText(),
                po.getCreateTime());
    }
}
