package com.gudu.xsd.modules.feedback.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gudu.xsd.modules.feedback.domain.model.Feedback;
import com.gudu.xsd.modules.feedback.domain.model.FeedbackPage;
import com.gudu.xsd.modules.feedback.domain.model.FeedbackStatus;
import com.gudu.xsd.modules.feedback.domain.model.FeedbackType;
import com.gudu.xsd.modules.feedback.domain.repository.FeedbackRepository;
import com.gudu.xsd.modules.feedback.infrastructure.mapper.FeedbackMapper;
import com.gudu.xsd.modules.feedback.infrastructure.po.FeedbackPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class MybatisFeedbackRepository implements FeedbackRepository {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final FeedbackMapper mapper;

    @Override
    public void save(Feedback f) {
        FeedbackPO po = new FeedbackPO();
        po.setId(f.id());
        po.setMemberId(f.memberId());
        po.setType(f.type().code());
        po.setSourceRef(f.sourceRef());
        po.setContent(f.content());
        po.setContact(f.contact());
        po.setImages(writeImages(f.images()));
        po.setClientInfo(f.clientInfo());
        po.setStatus(f.status().name());
        po.setReply(f.reply());
        po.setRepliedAt(f.repliedAt());
        po.setRepliedBy(f.repliedBy());
        if (f.id() == null) {
            po.setCreateTime(f.createTime());
            mapper.insert(po);
            f.identify(po.getId());
        } else {
            mapper.updateById(po);
        }
    }

    @Override
    public Optional<Feedback> findById(Long id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(mapper.selectById(id)).map(MybatisFeedbackRepository::toDomain);
    }

    @Override
    public long countByMemberSince(Long memberId, LocalDateTime since) {
        Long n = mapper.selectCount(new QueryWrapper<FeedbackPO>().eq("member_id", memberId).ge("create_time", since));
        return n == null ? 0 : n;
    }

    @Override
    public FeedbackPage page(FeedbackType type, FeedbackStatus status, int pageNum, int pageSize) {
        return toPage(new QueryWrapper<FeedbackPO>()
                .eq(type != null, "type", type == null ? null : type.code())
                .eq(status != null, "status", status == null ? null : status.name()), pageNum, pageSize);
    }

    @Override
    public FeedbackPage pageByMember(Long memberId, int pageNum, int pageSize) {
        return toPage(new QueryWrapper<FeedbackPO>().eq("member_id", memberId), pageNum, pageSize);
    }

    private FeedbackPage toPage(QueryWrapper<FeedbackPO> q, int pageNum, int pageSize) {
        IPage<FeedbackPO> p = mapper.selectPage(new Page<>(pageNum, pageSize),
                q.orderByDesc("create_time").orderByDesc("id"));
        return new FeedbackPage(p.getRecords().stream().map(MybatisFeedbackRepository::toDomain).toList(),
                p.getTotal(), pageNum, pageSize);
    }

    static Feedback toDomain(FeedbackPO po) {
        FeedbackType type;
        try {
            type = FeedbackType.ofOrGeneral(po.getType());
        } catch (RuntimeException e) {
            type = FeedbackType.GENERAL;
        }
        return Feedback.rehydrate(po.getId(), po.getMemberId(), type, po.getSourceRef(), po.getContent(),
                po.getContact(), readImages(po.getImages()), po.getClientInfo(), FeedbackStatus.of(po.getStatus()),
                po.getReply(), po.getRepliedAt(), po.getRepliedBy(), po.getCreateTime());
    }

    static String writeImages(List<String> images) {
        if (images == null || images.isEmpty()) return null;
        try {
            return JSON.writeValueAsString(images);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("反馈图片序列化失败", e);
        }
    }

    static List<String> readImages(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return JSON.readValue(json, new TypeReference<List<String>>() { });
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    @Override
    public int reassignAccount(Long from, Long to) {
        return mapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<FeedbackPO>()
                .eq("member_id", from).set("member_id", to));
    }

    @Override
    public int deleteDoneBefore(LocalDateTime cutoff, int limit) {
        return mapper.delete(new QueryWrapper<FeedbackPO>().eq("status", FeedbackStatus.DONE.name())
                .lt("update_time", cutoff).last("LIMIT " + Math.max(1, limit)));
    }

    @Override
    public List<Feedback> listByMember(Long memberId) {
        if (memberId == null) return List.of();
        return mapper.selectList(new QueryWrapper<FeedbackPO>().eq("member_id", memberId)
                        .orderByAsc("create_time").orderByAsc("id"))
                .stream().map(MybatisFeedbackRepository::toDomain).toList();
    }
}
