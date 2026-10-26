package com.gudu.xsd.modules.community.domain.repository;

import com.gudu.xsd.modules.community.domain.model.CounterDelta;
import com.gudu.xsd.modules.community.domain.model.Post;
import com.gudu.xsd.modules.community.domain.model.PostStatus;
import com.gudu.xsd.modules.community.domain.model.ReviewStatus;
import com.gudu.xsd.modules.community.domain.model.RiskLevel;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 帖子仓储（含帖子-话题关联）。 */
public interface PostRepository {

    /** 新建时写入话题关联并回填 id；更新只改状态与审核信息。 */
    void save(Post post);

    Optional<Post> findById(Long id);

    List<Post> findByIds(Collection<Long> ids);

    /** 图片列表里含任一给定地址的帖子（不含已删除；违规图片处置用）。 */
    List<Post> findByImageUrls(Collection<String> urls);

    /** 推荐流候选：since 之后发布、PUBLISHED + PUBLIC，按发布时间倒序最多 cap 条。 */
    List<Post> recentPublic(LocalDateTime since, int cap);

    /** 话题流：PUBLISHED + PUBLIC，按 (create_time, id) 降序 keyset；beforeTime 为空 = 第一页。 */
    List<Post> topicPage(Long topicId, LocalDateTime beforeTime, Long beforeId, LocalDateTime notAfter, int limit);

    /** 指定作者 since 之后的 PUBLISHED 帖（关注流读扩散 / 关注后回填 / 他们照着做了）。 */
    List<Post> recentByAuthors(Collection<Long> authorIds, LocalDateTime since, int cap);

    /** 计数回刷：在数据库现值上累加。 */
    void addCounts(Long postId, CounterDelta delta);

    /**
     * 审核队列，按 (create_time, id) 降序 keyset。PENDING = 未复核且未删除；DONE = 已有人工处置记录；
     * APPEALED = 有待处理的作者申诉。risk 为空不过滤。
     */
    List<Post> reviewQueue(ReviewStatus status, RiskLevel risk, LocalDateTime beforeTime, Long beforeId, int limit);

    long countCreatedSince(LocalDateTime since);

    long countCreatedSinceWithRisk(LocalDateTime since, RiskLevel risk);

    long countFromCookSince(LocalDateTime since);

    /** 待复核数；risk 为空 = 全部。 */
    long countPendingReview(RiskLevel risk);

    /** 被举报自动隐藏、尚未复核的帖子数。 */
    long countAutoHiddenPending();

    /** 作者的帖子数（不含已删除）；status 为空 = 全部。 */
    long countByAuthor(Long authorId, PostStatus status);

    /** 话题下 PUBLISHED 帖子数与最近封面。 */
    long countByTopic(Long topicId);

    List<String> recentCoversOfTopic(Long topicId, int limit);

    /** 账号合并（AccountMerged）：把归属 {@code from} 账号的记录改指 {@code to}。返回改动条数。 */
    int reassignAccount(Long from, Long to);
}
