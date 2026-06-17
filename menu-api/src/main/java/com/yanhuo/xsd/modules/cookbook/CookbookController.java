package com.yanhuo.xsd.modules.cookbook;

import com.yanhuo.xsd.common.R;
import com.yanhuo.xsd.modules.dish.Dish;
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
}
