package com.gudu.xsd.modules.cookbook;

import com.gudu.xsd.common.R;
import com.gudu.xsd.modules.dish.Dish;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/cookbook")
@RequiredArgsConstructor
@Tag(name = "菜库")
public class CookbookController {

    private final CookbookService svc;

    @PostMapping("/favorite/{dishId}")
    public R<?> favorite(@PathVariable Long dishId, @RequestParam Long memberId) {
        svc.favorite(memberId, dishId);
        return R.ok(null);
    }

    @DeleteMapping("/favorite/{dishId}")
    public R<?> unfavorite(@PathVariable Long dishId, @RequestParam Long memberId) {
        svc.unfavorite(memberId, dishId);
        return R.ok(null);
    }

    @GetMapping("/favorites")
    public R<List<Dish>> favorites(@RequestParam Long memberId) {
        return R.ok(svc.listFavorites(memberId));
    }

    @PostMapping("/done/{dishId}")
    public R<?> markDone(@PathVariable Long dishId,
                         @RequestParam Long memberId,
                         @RequestParam(required = false) String note) {
        svc.markDone(memberId, dishId, note);
        return R.ok(null);
    }

    @GetMapping("/done")
    public R<List<Dish>> done(@RequestParam Long memberId) {
        return R.ok(svc.listDone(memberId));
    }

    /**
     * 反向找菜：勾选「我有的食材」→ 推荐能做的菜（全匹配优先，部分匹配标注缺啥）。
     * @param ingredientIds 逗号分隔的食材 id 列表（如 1,2,3）
     */
    @GetMapping("/by-ingredients")
    public R<List<CookbookService.DishMatch>> findByIngredients(@RequestParam String ingredientIds) {
        List<Long> ids = new java.util.ArrayList<>();
        for (String s : ingredientIds.split(",")) {
            String t = s.trim();
            if (!t.isEmpty()) {
                ids.add(Long.valueOf(t));
            }
        }
        return R.ok(svc.findDishesByIngredients(ids));
    }
}
