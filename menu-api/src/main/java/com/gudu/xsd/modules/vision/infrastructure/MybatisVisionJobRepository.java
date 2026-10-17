package com.gudu.xsd.modules.vision.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.gudu.xsd.modules.vision.domain.model.RawRecipe;
import com.gudu.xsd.modules.vision.domain.model.RecognizedItem;
import com.gudu.xsd.modules.vision.domain.model.VisionJob;
import com.gudu.xsd.modules.vision.domain.model.VisionJobStatus;
import com.gudu.xsd.modules.vision.domain.model.VisionMode;
import com.gudu.xsd.modules.vision.domain.repository.VisionJobRepository;
import com.gudu.xsd.modules.vision.infrastructure.mapper.VisionJobMapper;
import com.gudu.xsd.modules.vision.infrastructure.po.VisionJobPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class MybatisVisionJobRepository implements VisionJobRepository {

    static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final VisionJobMapper mapper;

    @Override
    public void save(VisionJob job) {
        VisionJobPO po = new VisionJobPO();
        po.setId(job.id());
        po.setMemberId(job.memberId());
        po.setMode(job.mode().name());
        po.setImageUrl(job.imageUrl());
        po.setStatus(job.status().name());
        po.setError(job.error());
        po.setItemsJson(write(job.items()));
        po.setRecipeJson(job.recipe() == null ? null : write(job.recipe()));
        po.setAppliedAt(job.appliedAt());
        if (job.id() == null) {
            po.setCreateTime(job.createdAt());
            mapper.insert(po);
            job.identify(po.getId());
        } else {
            mapper.updateById(po);
            if (job.imageUrl() == null) { // updateById 默认忽略 null 字段：清图需显式置空
                mapper.update(null, new UpdateWrapper<VisionJobPO>().set("image_url", null).eq("id", job.id()));
            }
        }
    }

    @Override
    public Optional<VisionJob> findById(Long id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(mapper.selectById(id)).map(MybatisVisionJobRepository::toDomain);
    }

    @Override
    public List<VisionJob> findWithImageCreatedBefore(LocalDateTime createdBefore, int limit) {
        return mapper.selectList(new QueryWrapper<VisionJobPO>().isNotNull("image_url")
                        .lt("create_time", createdBefore).orderByAsc("id").last("LIMIT " + Math.max(1, limit)))
                .stream().map(MybatisVisionJobRepository::toDomain).toList();
    }

    @Override
    public int deleteCreatedBefore(LocalDateTime createdBefore) {
        return mapper.delete(new QueryWrapper<VisionJobPO>().lt("create_time", createdBefore));
    }

    static VisionJob toDomain(VisionJobPO po) {
        List<RecognizedItem> items = read(po.getItemsJson(), new TypeReference<List<RecognizedItem>>() { });
        RawRecipe recipe = read(po.getRecipeJson(), new TypeReference<RawRecipe>() { });
        return VisionJob.rehydrate(po.getId(), po.getMemberId(), VisionMode.of(po.getMode()), po.getImageUrl(),
                VisionJobStatus.parse(po.getStatus()), po.getError(), items == null ? List.of() : items, recipe,
                po.getCreateTime(), po.getAppliedAt());
    }

    static String write(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("识别结果序列化失败", e);
        }
    }

    static <T> T read(String json, TypeReference<T> type) {
        if (json == null || json.isBlank()) return null;
        try {
            return JSON.readValue(json, type);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
