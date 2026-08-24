package com.gudu.xsd.modules.nutrition;

import com.gudu.xsd.common.R;
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

    private final NutritionMetricService svc;

    /**
     * 全量返回营养指标字典。
     * 例外于 DESIGN.md §12.1（列表须分页）：营养指标为少量字典数据，用于下拉选择。
     */
    @GetMapping
    public R<List<NutritionMetric>> list() {
        return R.ok(svc.list());
    }

    @PostMapping
    public R<?> add(@RequestBody NutritionMetric m) {
        return R.ok(svc.add(m));
    }

    @PutMapping
    public R<?> update(@RequestBody NutritionMetric m) {
        svc.update(m);
        return R.ok(null);
    }

    @DeleteMapping("/{id}")
    public R<?> del(@PathVariable Long id) {
        svc.delete(id);
        return R.ok(null);
    }
}
