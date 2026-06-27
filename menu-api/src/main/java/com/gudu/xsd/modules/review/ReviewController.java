package com.gudu.xsd.modules.review;

import com.gudu.xsd.common.R;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/review")
@RequiredArgsConstructor
@Tag(name = "点评")
public class ReviewController {

    private final ReviewService svc;

    @PostMapping
    public R<Long> submit(@RequestBody @Valid ReviewSaveDTO dto) {
        return R.ok(svc.submit(dto));
    }

    @GetMapping("/dish/{dishId}")
    public R<List<Review>> listByDish(@PathVariable Long dishId) {
        return R.ok(svc.listByDish(dishId));
    }

    @GetMapping("/dish/{dishId}/avg")
    public R<Map<String, Object>> avg(@PathVariable Long dishId) {
        List<Review> reviews = svc.listByDish(dishId);
        List<Integer> stars = reviews.stream().map(Review::getStarRating).toList();
        Map<String, Object> result = Map.of(
            "star", svc.averageStar(stars).toPlainString(),
            "count", reviews.size());
        return R.ok(result);
    }
}
