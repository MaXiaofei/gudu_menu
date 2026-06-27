package com.gudu.xsd.modules.nutrition;

import com.gudu.xsd.common.R;
import com.gudu.xsd.modules.nutrition.mapper.NutritionMetricMapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 营养指标维度 CRUD（Task 5：纯字典；EAV 值在 Task 7 食材库）。
 */
@RestController
@RequestMapping("/nutrition/metric")
@RequiredArgsConstructor
@Tag(name = "营养指标")
public class NutritionMetricController {

    private final NutritionMetricMapper mapper;

    @GetMapping
    public R<List<NutritionMetric>> list() {
        return R.ok(mapper.selectList(null));
    }

    @PostMapping
    public R<?> add(@RequestBody NutritionMetric m) {
        mapper.insert(m);
        return R.ok(m.getId());
    }

    @PutMapping
    public R<?> update(@RequestBody NutritionMetric m) {
        mapper.updateById(m);
        return R.ok(null);
    }

    @DeleteMapping("/{id}")
    public R<?> del(@PathVariable Long id) {
        mapper.deleteById(id);
        return R.ok(null);
    }
}
