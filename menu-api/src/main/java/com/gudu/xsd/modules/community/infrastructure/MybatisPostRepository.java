package com.gudu.xsd.modules.community.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gudu.xsd.modules.community.domain.model.CounterDelta;
import com.gudu.xsd.modules.community.domain.model.ModerationState;
import com.gudu.xsd.modules.community.domain.model.Post;
import com.gudu.xsd.modules.community.domain.model.PostCounts;
import com.gudu.xsd.modules.community.domain.model.PostStatus;
import com.gudu.xsd.modules.community.domain.model.PostVisibility;
import com.gudu.xsd.modules.community.domain.model.ReviewStatus;
import com.gudu.xsd.modules.community.domain.model.RiskLevel;
import com.gudu.xsd.modules.community.domain.model.RiskScore;
import com.gudu.xsd.modules.community.domain.repository.PostRepository;
import com.gudu.xsd.modules.community.infrastructure.mapper.CommunityPostMapper;
import com.gudu.xsd.modules.community.infrastructure.mapper.CommunityPostTopicMapper;
import com.gudu.xsd.modules.community.infrastructure.po.CommunityPostPO;
import com.gudu.xsd.modules.community.infrastructure.po.CommunityPostTopicPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class MybatisPostRepository implements PostRepository {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final CommunityPostMapper mapper;
    private final CommunityPostTopicMapper topicMapper;

    @Override
    public void save(Post p) {
        CommunityPostPO po = new CommunityPostPO();
        po.setId(p.id());
        po.setStatus(p.status().name());
        ModerationState m = p.moderation();
        po.setRisk(m.risk().name());
        po.setRiskReasons(write(m.reasons()));
        po.setRiskScores(write(m.scores().stream().map(s -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("label", s.label());
            row.put("score", s.score());
            return row;
        }).toList()));
        po.setReviewed(m.reviewed() ? 1 : 0);
        if (p.id() != null) {
            mapper.updateById(po);
            return;
        }
        po.setAuthorId(p.authorId());
        po.setTitle(p.title());
        po.setText(p.text());
        po.setImages(write(p.images()));
        po.setBlockedImages(p.blockedImages().isEmpty() ? null : write(p.blockedImages()));
        po.setDishId(p.dishId());
        po.setVisibility(p.visibility().name());
        po.setFromCookRecordId(p.fromCookRecordId());
        po.setCity(p.city());
        po.setCoverRatio(BigDecimal.valueOf(p.coverRatio()).setScale(2, RoundingMode.HALF_UP));
        po.setLikeCount(0);
        po.setCollectCount(0);
        po.setCommentCount(0);
        po.setCopyCount(0);
        po.setCreateTime(p.createTime());
        mapper.insert(po);
        p.identify(po.getId());
        for (Long topicId : p.topicIds()) {
            CommunityPostTopicPO rel = new CommunityPostTopicPO();
            rel.setPostId(po.getId());
            rel.setTopicId(topicId);
            topicMapper.insert(rel);
        }
    }

    @Override
    public Optional<Post> findById(Long id) {
        if (id == null) return Optional.empty();
        return findByIds(List.of(id)).stream().findFirst();
    }

    @Override
    public List<Post> findByIds(Collection<Long> ids) {
        List<Long> list = ids == null ? List.of() : ids.stream().filter(Objects::nonNull).distinct().toList();
        if (list.isEmpty()) return List.of();
        return toDomain(mapper.selectBatchIds(list));
    }

    @Override
    public List<Post> findByImageUrls(Collection<String> urls) {
        List<String> list = urls == null ? List.of() : urls.stream().filter(u -> u != null && !u.isBlank()).toList();
        if (list.isEmpty()) return List.of();
        QueryWrapper<CommunityPostPO> q = new QueryWrapper<CommunityPostPO>().ne("status", PostStatus.DELETED.name());
        // images 存 JSON 数组字符串：先按带引号的地址 LIKE 粗筛，再在内存里精确匹配（地址里的 _ 是 LIKE 通配符）
        q.and(w -> {
            for (String u : list) w.or().like("images", "\"" + u + "\"");
        });
        return toDomain(mapper.selectList(q)).stream()
                .filter(p -> p.images().stream().anyMatch(list::contains)).toList();
    }

    @Override
    public List<Post> recentPublic(LocalDateTime since, int cap) {
        return toDomain(mapper.selectList(publicPublished().ge("create_time", since)
                .orderByDesc("create_time").orderByDesc("id").last("LIMIT " + cap)));
    }

    @Override
    public List<Post> topicPage(Long topicId, LocalDateTime beforeTime, Long beforeId, LocalDateTime notAfter,
                                int limit) {
        QueryWrapper<CommunityPostPO> w = publicPublished()
                .inSql("id", "SELECT post_id FROM community_post_topic WHERE topic_id = " + topicId.longValue())
                .le(notAfter != null, "create_time", notAfter);
        keysetBefore(w, beforeTime, beforeId);
        return toDomain(mapper.selectList(w.orderByDesc("create_time").orderByDesc("id").last("LIMIT " + limit)));
    }

    @Override
    public List<Post> recentByAuthors(Collection<Long> authorIds, LocalDateTime since, int cap) {
        if (authorIds == null || authorIds.isEmpty()) return List.of();
        return toDomain(mapper.selectList(new QueryWrapper<CommunityPostPO>()
                .eq("status", PostStatus.PUBLISHED.name()).in("author_id", authorIds).ge("create_time", since)
                .orderByDesc("create_time").orderByDesc("id").last("LIMIT " + cap)));
    }

    @Override
    public void addCounts(Long postId, CounterDelta d) {
        if (d == null || d.isZero()) return;
        mapper.addCounts(postId, d.likes(), d.collects(), d.comments(), d.copies());
    }

    @Override
    public List<Post> reviewQueue(ReviewStatus status, RiskLevel risk, LocalDateTime beforeTime, Long beforeId,
                                  int limit) {
        QueryWrapper<CommunityPostPO> w = new QueryWrapper<CommunityPostPO>()
                .ne("status", PostStatus.DELETED.name()).eq(risk != null, "risk", risk == null ? null : risk.name());
        if (status == ReviewStatus.APPEALED) {
            w.inSql("id", "SELECT post_id FROM community_appeal WHERE handled = 0");
        } else if (status == ReviewStatus.DONE) {
            w.eq("reviewed", 1).inSql("id", "SELECT post_id FROM community_moderation_log");
        } else {
            w.eq("reviewed", 0);
        }
        keysetBefore(w, beforeTime, beforeId);
        return toDomain(mapper.selectList(w.orderByDesc("create_time").orderByDesc("id").last("LIMIT " + limit)));
    }

    @Override
    public long countCreatedSince(LocalDateTime since) {
        return nz(mapper.selectCount(new QueryWrapper<CommunityPostPO>().ge("create_time", since)));
    }

    @Override
    public long countCreatedSinceWithRisk(LocalDateTime since, RiskLevel risk) {
        return nz(mapper.selectCount(new QueryWrapper<CommunityPostPO>().ge("create_time", since)
                .eq("risk", risk.name())));
    }

    @Override
    public long countFromCookSince(LocalDateTime since) {
        return nz(mapper.selectCount(new QueryWrapper<CommunityPostPO>().ge("create_time", since)
                .isNotNull("from_cook_record_id")));
    }

    @Override
    public long countPendingReview(RiskLevel risk) {
        return nz(mapper.selectCount(new QueryWrapper<CommunityPostPO>().eq("reviewed", 0)
                .ne("status", PostStatus.DELETED.name()).eq(risk != null, "risk", risk == null ? null : risk.name())));
    }

    @Override
    public long countAutoHiddenPending() {
        return nz(mapper.selectCount(new QueryWrapper<CommunityPostPO>().eq("reviewed", 0)
                .eq("status", PostStatus.HIDDEN.name())));
    }

    @Override
    public long countByAuthor(Long authorId, PostStatus status) {
        return nz(mapper.selectCount(new QueryWrapper<CommunityPostPO>().eq("author_id", authorId)
                .ne("status", PostStatus.DELETED.name())
                .eq(status != null, "status", status == null ? null : status.name())));
    }

    @Override
    public long countByTopic(Long topicId) {
        return nz(mapper.selectCount(publicPublished()
                .inSql("id", "SELECT post_id FROM community_post_topic WHERE topic_id = " + topicId.longValue())));
    }

    @Override
    public List<String> recentCoversOfTopic(Long topicId, int limit) {
        return topicPage(topicId, null, null, null, limit).stream().map(Post::cover).filter(Objects::nonNull).toList();
    }

    private static QueryWrapper<CommunityPostPO> publicPublished() {
        return new QueryWrapper<CommunityPostPO>().eq("status", PostStatus.PUBLISHED.name())
                .eq("visibility", PostVisibility.PUBLIC.name());
    }

    private static void keysetBefore(QueryWrapper<CommunityPostPO> w, LocalDateTime beforeTime, Long beforeId) {
        if (beforeTime == null || beforeId == null) return;
        w.and(x -> x.lt("create_time", beforeTime).or(y -> y.eq("create_time", beforeTime).lt("id", beforeId)));
    }

    private List<Post> toDomain(List<CommunityPostPO> rows) {
        if (rows == null || rows.isEmpty()) return List.of();
        List<Long> ids = rows.stream().map(CommunityPostPO::getId).toList();
        Map<Long, List<Long>> topics = topicMapper.selectList(new QueryWrapper<CommunityPostTopicPO>().in("post_id", ids))
                .stream().collect(Collectors.groupingBy(CommunityPostTopicPO::getPostId,
                        Collectors.mapping(CommunityPostTopicPO::getTopicId, Collectors.toList())));
        List<Post> out = new ArrayList<>(rows.size());
        for (CommunityPostPO po : rows) {
            ModerationState m = new ModerationState(RiskLevel.of(po.getRisk()), readStrings(po.getRiskReasons()),
                    readScores(po.getRiskScores()), po.getReviewed() != null && po.getReviewed() == 1);
            out.add(Post.rehydrate(po.getId(), po.getAuthorId(), po.getTitle(), po.getText(),
                    readStrings(po.getImages()), readStrings(po.getBlockedImages()), po.getDishId(),
                    topics.getOrDefault(po.getId(), List.of()), PostVisibility.of(po.getVisibility()),
                    po.getFromCookRecordId(), po.getCity(),
                    po.getCoverRatio() == null ? 1.0 : po.getCoverRatio().doubleValue(), po.getCreateTime(),
                    PostStatus.of(po.getStatus()), m,
                    new PostCounts(nzi(po.getLikeCount()), nzi(po.getCollectCount()), nzi(po.getCommentCount()),
                            nzi(po.getCopyCount()))));
        }
        return out;
    }

    private static long nz(Long n) {
        return n == null ? 0 : n;
    }

    private static long nzi(Integer n) {
        return n == null ? 0 : n;
    }

    static String write(List<?> list) {
        if (list == null || list.isEmpty()) return "[]";
        try {
            return JSON.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("帖子字段序列化失败", e);
        }
    }

    static List<String> readStrings(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return JSON.readValue(json, new TypeReference<List<String>>() { });
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    static List<RiskScore> readScores(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            List<Map<String, Object>> rows = JSON.readValue(json, new TypeReference<List<Map<String, Object>>>() { });
            return rows.stream().map(r -> new RiskScore(String.valueOf(r.get("label")),
                    r.get("score") instanceof Number n ? n.doubleValue() : 0)).toList();
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    @Override
    public int reassignAccount(Long from, Long to) {
        return mapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<CommunityPostPO>()
                .eq("author_id", from).set("author_id", to));
    }
}
