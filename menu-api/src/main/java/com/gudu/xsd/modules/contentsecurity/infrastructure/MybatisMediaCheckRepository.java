package com.gudu.xsd.modules.contentsecurity.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gudu.xsd.modules.contentsecurity.domain.model.ContentPurpose;
import com.gudu.xsd.modules.contentsecurity.domain.model.MediaCheck;
import com.gudu.xsd.modules.contentsecurity.domain.model.MediaCheckStatus;
import com.gudu.xsd.modules.contentsecurity.domain.model.MediaReviewStatus;
import com.gudu.xsd.modules.contentsecurity.domain.repository.MediaCheckRepository;
import com.gudu.xsd.modules.contentsecurity.infrastructure.mapper.ContentMediaCheckMapper;
import com.gudu.xsd.modules.contentsecurity.infrastructure.po.ContentMediaCheckPO;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class MybatisMediaCheckRepository implements MediaCheckRepository {

    /** 复核筛选：未复核。 */
    static final String REVIEW_PENDING = "PENDING";

    private final ContentMediaCheckMapper mapper;

    public MybatisMediaCheckRepository(ContentMediaCheckMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(MediaCheck c) {
        ContentMediaCheckPO po = new ContentMediaCheckPO();
        po.setId(c.id());
        po.setTraceId(c.traceId());
        po.setMediaUrl(c.mediaUrl());
        po.setMemberId(c.memberId());
        po.setPurpose(c.purpose().name());
        po.setStatus(c.status().name());
        po.setLabel(c.label());
        po.setReviewStatus(c.reviewStatus() == null ? null : c.reviewStatus().name());
        po.setReviewedBy(c.reviewedBy());
        po.setReviewedAt(c.reviewedAt());
        if (c.id() == null) {
            po.setCreateTime(c.createTime());
            mapper.insert(po);
            c.identify(po.getId());
        } else {
            mapper.updateById(po);
        }
    }

    @Override
    public Optional<MediaCheck> findByTraceId(String traceId) {
        if (traceId == null || traceId.isBlank()) return Optional.empty();
        return Optional.ofNullable(mapper.selectOne(new QueryWrapper<ContentMediaCheckPO>().eq("trace_id", traceId)
                .last("LIMIT 1"))).map(MybatisMediaCheckRepository::toDomain);
    }

    @Override
    public Optional<MediaCheck> latestByUrl(String mediaUrl) {
        if (mediaUrl == null || mediaUrl.isBlank()) return Optional.empty();
        return Optional.ofNullable(mapper.selectOne(new QueryWrapper<ContentMediaCheckPO>().eq("media_url", mediaUrl)
                .orderByDesc("id").last("LIMIT 1"))).map(MybatisMediaCheckRepository::toDomain);
    }

    @Override
    public Optional<MediaCheck> findById(Long id) {
        return id == null ? Optional.empty()
                : Optional.ofNullable(mapper.selectById(id)).map(MybatisMediaCheckRepository::toDomain);
    }

    @Override
    public List<MediaCheck> pageViolations(String review, String purpose, Long accountId, int offset, int limit) {
        return mapper.selectList(violations(review, purpose, accountId).orderByDesc("create_time").orderByDesc("id")
                        .last("LIMIT " + Math.max(0, offset) + ", " + Math.max(1, limit)))
                .stream().map(MybatisMediaCheckRepository::toDomain).toList();
    }

    @Override
    public List<String> urlsByAccount(Long accountId, ContentPurpose purpose) {
        if (accountId == null || purpose == null) return List.of();
        return mapper.selectList(new QueryWrapper<ContentMediaCheckPO>().select("id", "media_url")
                        .eq("member_id", accountId).eq("purpose", purpose.name()))
                .stream().map(ContentMediaCheckPO::getMediaUrl).filter(java.util.Objects::nonNull).distinct().toList();
    }

    @Override
    public long countViolations(String review, String purpose, Long accountId) {
        return mapper.selectCount(violations(review, purpose, accountId));
    }

    private static QueryWrapper<ContentMediaCheckPO> violations(String review, String purpose, Long accountId) {
        QueryWrapper<ContentMediaCheckPO> q = new QueryWrapper<ContentMediaCheckPO>()
                .eq("status", MediaCheckStatus.RISKY.name());
        if (review != null && !review.isBlank()) {
            if (REVIEW_PENDING.equals(review)) {
                q.isNull("review_status");
            } else {
                MediaReviewStatus r = MediaReviewStatus.of(review);
                q.eq("review_status", r == null ? review : r.name());
            }
        }
        if (purpose != null && !purpose.isBlank()) q.eq("purpose", purpose.trim());
        if (accountId != null) q.eq("member_id", accountId);
        return q;
    }

    static MediaCheck toDomain(ContentMediaCheckPO po) {
        ContentPurpose purpose;
        try {
            purpose = ContentPurpose.valueOf(po.getPurpose());
        } catch (RuntimeException e) {
            purpose = ContentPurpose.UPLOAD;
        }
        return MediaCheck.rehydrate(po.getId(), po.getTraceId(), po.getMediaUrl(), po.getMemberId(), purpose,
                MediaCheckStatus.of(po.getStatus()), po.getLabel() == null ? 0 : po.getLabel(), po.getCreateTime(),
                MediaReviewStatus.of(po.getReviewStatus()), po.getReviewedBy(), po.getReviewedAt());
    }

    @Override
    public int reassignAccount(Long from, Long to) {
        return mapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<ContentMediaCheckPO>()
                .eq("member_id", from).set("member_id", to));
    }
}
