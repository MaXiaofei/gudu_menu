package com.gudu.xsd.modules.review;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gudu.xsd.common.privacy.PersonalDataSection;
import com.gudu.xsd.common.privacy.PersonalDataSubject;
import com.gudu.xsd.common.tenant.TenantContextHolder;
import com.gudu.xsd.modules.review.mapper.ReviewMapper;
import com.gudu.xsd.modules.review.mapper.ReviewScoreMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 导出我的数据（C.2）：本人档案发表的点评与维度评分。系统模式 + 显式 member_id ∈ 本人档案。 */
@Component
@RequiredArgsConstructor
public class ReviewPersonalData implements PersonalDataSection {

    private final ReviewMapper reviewMapper;
    private final ReviewScoreMapper scoreMapper;

    @Override
    public String key() {
        return "reviews";
    }

    @Override
    public int order() {
        return 60;
    }

    @Override
    public Object export(PersonalDataSubject s) {
        if (s.profileIds().isEmpty()) return List.of();
        return TenantContextHolder.callAsSystem(() -> {
            List<Review> reviews = reviewMapper.selectList(new QueryWrapper<Review>()
                    .in("member_id", s.profileIds()).orderByAsc("create_time").orderByAsc("id"));
            if (reviews.isEmpty()) return List.<Map<String, Object>>of();
            Map<Long, List<ReviewScore>> scores = scoreMapper.selectList(new QueryWrapper<ReviewScore>()
                            .in("review_id", reviews.stream().map(Review::getId).toList()))
                    .stream().collect(Collectors.groupingBy(ReviewScore::getReviewId));
            return reviews.stream().map(r -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", r.getId());
                m.put("familyId", r.getFamilyId());
                m.put("profileId", r.getMemberId());
                m.put("dishId", r.getDishId());
                m.put("menuId", r.getMenuId());
                m.put("starRating", r.getStarRating());
                m.put("text", r.getText());
                m.put("images", r.getImages());
                m.put("createTime", r.getCreateTime());
                m.put("scores", scores.getOrDefault(r.getId(), List.of()).stream()
                        .map(x -> Map.of("dimensionId", x.getDimensionId(), "score", x.getScore())).toList());
                return m;
            }).toList();
        });
    }
}
