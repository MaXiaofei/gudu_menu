package com.gudu.xsd.modules.nutrition;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gudu.xsd.common.R;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/ingredient")
@RequiredArgsConstructor
@Tag(name = "食材库")
public class IngredientController {

    private final IngredientService svc;

    @GetMapping
    public R<IPage<IngredientVO>> list(IngredientPageQuery q) {
        return R.ok(svc.pageWithNutrition(q));
    }

    /** 食材详情（编辑页：名称/默认单位/单价/品类/食用属性/换算数 + 营养）。 */
    @GetMapping("/{id}")
    public R<IngredientVO> detail(@PathVariable Long id) {
        return R.ok(svc.detail(id));
    }

    /** 该食材营养：metricId -> value(per 100g)。 */
    @GetMapping("/{id}/nutrition")
    public R<Map<Long, BigDecimal>> nutrition(@PathVariable Long id) {
        return R.ok(svc.nutritionOf(id));
    }

    @PostMapping
    public R<?> add(@RequestBody IngredientSaveDTO dto) {
        svc.saveWithNutrition(dto.getIngredient(), dto.getNutritions());
        return R.ok(dto.getIngredient().getId());
    }

    @PutMapping
    public R<?> update(@RequestBody Ingredient ing) {
        svc.updateById(ing);
        return R.ok(null);
    }

    @DeleteMapping("/{id}")
    public R<?> del(@PathVariable Long id) {
        svc.removeById(id);
        return R.ok(null);
    }
}
