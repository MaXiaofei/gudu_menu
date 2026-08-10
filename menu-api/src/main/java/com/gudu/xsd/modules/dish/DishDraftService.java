package com.gudu.xsd.modules.dish;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gudu.xsd.common.BizException;
import com.gudu.xsd.modules.dish.DishDraftDTO.Detail;
import com.gudu.xsd.modules.dish.DishDraftDTO.DraftIngredient;
import com.gudu.xsd.modules.dish.DishDraftDTO.ListItem;
import com.gudu.xsd.modules.dish.DishDraftDTO.Save;
import com.gudu.xsd.modules.dish.mapper.DishDraftMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 写菜谱草稿（DESIGN.md §16.4）：独立表不污染 dish；JSON 列存用量自由文本/步骤原文。
 * 归属按 sa-token 当前成员隔离（AuthService 登录时写入 currentMemberId）。
 */
@Service
@RequiredArgsConstructor
public class DishDraftService {

    private final DishDraftMapper mapper;
    private final ObjectMapper objectMapper;

    private Long currentUserId() {
        return StpUtil.getSession().getLong("currentMemberId");
    }

    private void assertOwner(DishDraft d) {
        if (d == null || !currentUserId().equals(d.getUserId())) {
            throw new BizException("草稿不存在");
        }
    }

    /** 保存草稿：无 id 新建、有 id 更新（仅本人可更新）。返回草稿 id。 */
    public Long save(Save dto) {
        Long uid = currentUserId();
        DishDraft d;
        if (dto.getId() != null) {
            d = mapper.selectById(dto.getId());
            assertOwner(d);
        } else {
            d = new DishDraft();
            d.setUserId(uid);
        }
        d.setName(dto.getName());
        d.setCoverUrl(dto.getCoverUrl());
        d.setPrepTime(dto.getPrepTime());
        d.setCookTime(dto.getCookTime());
        d.setDifficulty(dto.getDifficulty());
        d.setNote(dto.getNote());
        d.setTags(toJson(dto.getTagIds()));
        d.setCuisineIds(toJson(dto.getCuisineIds()));
        d.setIngredients(toJson(dto.getIngredients()));
        d.setSteps(toJson(dto.getSteps()));
        d.setSourceType(dto.getSourceUrl() != null ? "url" : "manual");
        d.setSourceUrl(dto.getSourceUrl());
        if (d.getId() == null) {
            mapper.insert(d);
        } else {
            mapper.updateById(d);
        }
        return d.getId();
    }

    /** 草稿箱列表（本人，按更新时间倒序；分页，DESIGN.md §12）。轻量不解析 JSON 明细，只计数。 */
    public IPage<ListItem> list(long pageNum, long pageSize) {
        return mapper.selectPage(
                        new Page<>(pageNum, pageSize),
                        new QueryWrapper<DishDraft>()
                                .eq("user_id", currentUserId())
                                .orderByDesc("update_time"))
                .convert(d -> {
                    ListItem it = new ListItem();
                    it.setId(d.getId());
                    it.setName(d.getName());
                    it.setCoverUrl(d.getCoverUrl());
                    it.setIngredientCount(sizeOf(d.getIngredients()));
                    it.setStepCount(sizeOf(d.getSteps()));
                    it.setUpdateTime(d.getUpdateTime());
                    return it;
                });
    }

    /** 草稿详情（恢复编辑回填；含 JSON 明细，仅本人）。 */
    public Detail detail(Long id) {
        DishDraft d = mapper.selectById(id);
        assertOwner(d);
        Detail v = new Detail();
        v.setId(d.getId());
        v.setName(d.getName());
        v.setCoverUrl(d.getCoverUrl());
        v.setPrepTime(d.getPrepTime());
        v.setCookTime(d.getCookTime());
        v.setDifficulty(d.getDifficulty());
        v.setNote(d.getNote());
        v.setTagIds(fromJson(d.getTags(), new TypeReference<List<Long>>() {}));
        v.setCuisineIds(fromJson(d.getCuisineIds(), new TypeReference<List<Long>>() {}));
        v.setIngredients(fromJson(d.getIngredients(), new TypeReference<List<DraftIngredient>>() {}));
        v.setSteps(fromJson(d.getSteps(), new TypeReference<List<DishStep>>() {}));
        v.setSourceUrl(d.getSourceUrl());
        v.setUpdateTime(d.getUpdateTime());
        return v;
    }

    /** 删除草稿（仅本人；发布成功后前端也会调用清掉）。 */
    public void delete(Long id) {
        DishDraft d = mapper.selectById(id);
        assertOwner(d);
        mapper.deleteById(id);
    }

    // ===== JSON 序列化/反序列化（JSON 列损坏时容错为 null/空） =====

    private String toJson(Object o) {
        if (o == null) return null;
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            return null;
        }
    }

    private <T> T fromJson(String json, TypeReference<T> ref) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, ref);
        } catch (Exception e) {
            return null;
        }
    }

    private int sizeOf(String json) {
        List<?> list = fromJson(json, new TypeReference<List<Object>>() {});
        return list == null ? 0 : list.size();
    }
}
