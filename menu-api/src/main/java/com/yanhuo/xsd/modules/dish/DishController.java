package com.yanhuo.xsd.modules.dish;

import com.yanhuo.xsd.common.R;
import com.yanhuo.xsd.modules.dish.DishService.DishDetail;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/dish")
@RequiredArgsConstructor
@Tag(name = "菜品")
public class DishController {

    private final DishService svc;

    @GetMapping
    public R<List<Dish>> list() {
        return R.ok(svc.list());
    }

    @GetMapping("/{id}")
    public R<DishDetail> detail(@PathVariable Long id) {
        return R.ok(svc.detail(id));
    }

    @PostMapping
    public R<?> save(@RequestBody DishSaveDTO dto) {
        svc.saveWithSteps(dto.getDish(), dto.getSteps());
        return R.ok(dto.getDish().getId());
    }

    @PutMapping
    public R<?> update(@RequestBody DishSaveDTO dto) {
        svc.saveWithSteps(dto.getDish(), dto.getSteps());
        return R.ok(null);
    }

    @DeleteMapping("/{id}")
    public R<?> del(@PathVariable Long id) {
        svc.removeById(id);
        return R.ok(null);
    }
}
