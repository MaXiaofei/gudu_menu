package com.gudu.xsd.modules.nutrition;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gudu.xsd.common.R;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
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

    /** 某食材的单位换算列表（用户可编辑）。 */
    @GetMapping("/{id}/unit-grams")
    public R<List<IngredientUnitGram>> unitGrams(@PathVariable Long id) {
        return R.ok(svc.listUnitGrams(id));
    }

    /** 整体替换某食材的单位换算（用户编辑保存）。 */
    @PutMapping("/{id}/unit-grams")
    public R<?> saveUnitGrams(@PathVariable Long id,
                              @RequestBody List<IngredientUnitGram> rows) {
        svc.replaceUnitGrams(id, rows);
        return R.ok(null);
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
