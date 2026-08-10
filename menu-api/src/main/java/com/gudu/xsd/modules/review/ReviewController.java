package com.gudu.xsd.modules.review;

import com.gudu.xsd.common.R;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 点评接口（V43：支持食集评价）。
 *
 * <p>统一评价页：GET /review/menu-overview/{menuId}（食集整体 + 每道菜已评状态）；
 * 我的评价：GET /review/mine（评价历史 + 待评价食集）。
 */
@RestController
@RequestMapping("/review")
@RequiredArgsConstructor
@Tag(name = "点评")
public class ReviewController {

    private final ReviewService svc;

    /** 提交点评（菜品或食集整体，dishId/menuId 二选一）。 */
    @PostMapping
    public R<Long> submit(@RequestBody @Valid ReviewSaveDTO dto) {
        return R.ok(svc.submit(dto));
    }

    /** 某菜的所有点评（最新优先）。 */
    @GetMapping("/dish/{dishId}")
    public R<List<Review>> listByDish(@PathVariable Long dishId) {
        return R.ok(svc.listByDish(dishId));
    }

    /** 某食集的所有整体评价（最新优先）。V43。 */
    @GetMapping("/menu/{menuId}")
    public R<List<Review>> listByMenu(@PathVariable Long menuId) {
        return R.ok(svc.listByMenu(menuId));
    }

    /** 某菜均分（菜谱详情展示）。 */
    @GetMapping("/dish/{dishId}/avg")
    public R<Map<String, Object>> avg(@PathVariable Long dishId) {
        List<Review> reviews = svc.listByDish(dishId);
        List<Integer> stars = reviews.stream().map(Review::getStarRating).toList();
        Map<String, Object> result = Map.of(
            "star", svc.averageStar(stars).toPlainString(),
            "count", reviews.size());
        return R.ok(result);
    }

    /** 统一评价页数据：食集整体已评状态 + 每道菜已评状态。V43。 */
    @GetMapping("/menu-overview/{menuId}")
    public R<MenuReviewOverviewVO> menuOverview(@PathVariable Long menuId) {
        return R.ok(svc.menuOverview(menuId));
    }

    /** 我的评价：评价历史 + 待评价食集。V43。 */
    /** 我的评价（历史 + 待评价食集）。个人级聚合数据，量小，DESIGN.md §12 全量例外。 */
    @GetMapping("/mine")
    public R<MyReviewsVO> mine() {
        return R.ok(svc.mine());
    }
}
