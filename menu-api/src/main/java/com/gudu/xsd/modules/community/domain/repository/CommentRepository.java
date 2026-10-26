package com.gudu.xsd.modules.community.domain.repository;

import com.gudu.xsd.modules.community.domain.model.Comment;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface CommentRepository {

    void save(Comment comment);

    Optional<Comment> findById(Long id);

    /** 顶层评论，按 id 升序（先评先看）keyset：afterId 为空 = 第一页。 */
    List<Comment> topLevel(Long postId, Long afterId, int limit);

    /** 回复，按 parentId 分组、组内 id 升序。 */
    Map<Long, List<Comment>> replies(Collection<Long> parentIds);

    /** 账号合并（AccountMerged）：把归属 {@code from} 账号的记录改指 {@code to}。返回改动条数。 */
    int reassignAccount(Long from, Long to);
}
