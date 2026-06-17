package com.yanhuo.xsd.modules.review;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.yanhuo.xsd.common.BizException;
import com.yanhuo.xsd.modules.review.mapper.ReviewMapper;
import com.yanhuo.xsd.modules.review.mapper.ReviewScoreMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ReviewService {

    private final ReviewMapper reviewMapper;
    private final ReviewScoreMapper reviewScoreMapper;

    public ReviewService(ReviewMapper reviewMapper, ReviewScoreMapper reviewScoreMapper) {
        this.reviewMapper = reviewMapper;
        this.reviewScoreMapper = reviewScoreMapper;
    }

    /** 提交点评：当前就餐成员 + 级联维度分。 */
    @Transactional
    public Long submit(ReviewSaveDTO dto) {
        Long memberId = StpUtil.getSession().getLong("currentMemberId");
        if (memberId == null) throw new BizException("请先选择就餐成员");
        Review r = new Review();
        r.setDishId(dto.getDishId());
        r.setMemberId(memberId);
        r.setStarRating(dto.getStarRating());
        r.setText(dto.getText());
        r.setImages(dto.getImages() == null ? null : String.join(",", dto.getImages()));
        reviewMapper.insert(r);
        if (dto.getDimensionScores() != null) {
            dto.getDimensionScores().forEach((dimId, score) -> {
                if (score == null || score < 1 || score > 5) throw new BizException("维度分需为 1-5");
                ReviewScore s = new ReviewScore();
                s.setReviewId(r.getId());
                s.setDimensionId(dimId);
                s.setScore(score);
                reviewScoreMapper.insert(s);
            });
        }
        return r.getId();
    }

    /** 某菜的所有点评（最新优先）。 */
    public List<Review> listByDish(Long dishId) {
        return reviewMapper.selectList(
            new QueryWrapper<Review>().eq("dish_id", dishId).orderByDesc("create_time"));
    }

    // ===== 纯函数：平均分（TDD 覆盖） =====

    /** 总评星级均分（保留 1 位）。 */
    public BigDecimal averageStar(List<Integer> stars) {
        if (stars == null || stars.isEmpty()) return BigDecimal.ZERO;
        BigDecimal sum = stars.stream().map(BigDecimal::new)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(new BigDecimal(stars.size()), 1, RoundingMode.HALF_UP);
    }

    /** 各维度均分：dimensionId -> "x.x"。 */
    public Map<Long, String> averageByDimension(List<? extends DimensionScore> rows) {
        Map<Long, List<Integer>> grouped = rows.stream()
            .filter(r -> r.dimensionId() != null && r.score() != null)
            .collect(Collectors.groupingBy(DimensionScore::dimensionId,
                Collectors.mapping(DimensionScore::score, Collectors.toList())));
        return grouped.entrySet().stream().collect(Collectors.toMap(
            Map.Entry::getKey,
            e -> averageStar(e.getValue()).toPlainString()));
    }

    /** 维度分行接口。 */
    public interface DimensionScore {
        Long dimensionId();
        Integer score();
    }
}
