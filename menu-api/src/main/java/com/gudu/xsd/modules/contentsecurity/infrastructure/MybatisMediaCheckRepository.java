package com.gudu.xsd.modules.contentsecurity.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gudu.xsd.modules.contentsecurity.domain.model.ContentPurpose;
import com.gudu.xsd.modules.contentsecurity.domain.model.MediaCheck;
import com.gudu.xsd.modules.contentsecurity.domain.model.MediaCheckStatus;
import com.gudu.xsd.modules.contentsecurity.domain.repository.MediaCheckRepository;
import com.gudu.xsd.modules.contentsecurity.infrastructure.mapper.ContentMediaCheckMapper;
import com.gudu.xsd.modules.contentsecurity.infrastructure.po.ContentMediaCheckPO;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class MybatisMediaCheckRepository implements MediaCheckRepository {

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

    static MediaCheck toDomain(ContentMediaCheckPO po) {
        ContentPurpose purpose;
        try {
            purpose = ContentPurpose.valueOf(po.getPurpose());
        } catch (RuntimeException e) {
            purpose = ContentPurpose.UPLOAD;
        }
        return MediaCheck.rehydrate(po.getId(), po.getTraceId(), po.getMediaUrl(), po.getMemberId(), purpose,
                MediaCheckStatus.of(po.getStatus()), po.getLabel() == null ? 0 : po.getLabel(), po.getCreateTime());
    }
}
