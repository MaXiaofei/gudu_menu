package com.yanhuo.xsd.modules.dish;

import com.yanhuo.xsd.common.R;
import com.yanhuo.xsd.modules.dish.DishService.DishDetail;
import com.yanhuo.xsd.modules.member.MpPerm;
import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/dish")
@RequiredArgsConstructor
@Tag(name = "菜品")
public class DishController {

    private final DishService svc;
    private final DishQueryService querySvc;
    private final RecipeImporter recipeImporter;

    @GetMapping
    public R<List<Dish>> list() {
        return R.ok(svc.list());
    }

    /** 多维搜索分页（放在 /{id} 之前，避免被路径变量吞掉）。 */
    @GetMapping("/search")
    public R<IPage<Dish>> search(DishSearchDTO q) {
        return R.ok(svc.search(q));
    }

    @GetMapping("/{id}")
    public R<DishDetail> detail(@PathVariable Long id) {
        return R.ok(svc.detail(id));
    }

    /** 份数营养：复用 NutritionCalcService。 */
    @GetMapping("/{id}/nutrition")
    public R<Map<Long, BigDecimal>> nutrition(@PathVariable Long id,
                                              @RequestParam(required = false) BigDecimal serving) {
        return R.ok(querySvc.nutrition(id, serving));
    }

    @GetMapping("/{id}/history")
    public R<List<DishHistory>> history(@PathVariable Long id) {
        return R.ok(querySvc.history(id));
    }

    @DeleteMapping("/{id}/history/{hid}")
    public R<?> deleteHistory(@PathVariable Long id, @PathVariable Long hid) {
        querySvc.deleteHistory(id, hid);
        return R.ok(null);
    }

    @DeleteMapping("/{id}/history")
    public R<?> clearHistory(@PathVariable Long id) {
        querySvc.clearHistory(id);
        return R.ok(null);
    }

    @PostMapping
    @MpPerm("dish.create")
    public R<?> save(@RequestBody DishSaveDTO dto) {
        svc.saveFull(dto);
        return R.ok(dto.getDish().getId());
    }

    /** URL 导入菜谱：抓网页 → 解析 → 落库，返回新菜品 id。 */
    @PostMapping("/import-url")
    @MpPerm("dish.create")
    public R<Long> importUrl(@RequestParam String url) {
        DishSaveDTO dto = recipeImporter.importFromUrl(url);
        svc.saveFull(dto);
        return R.ok(dto.getDish().getId());
    }

    /** 更新：先存历史快照，再保存（Controller 编排，避开 Service 循环依赖）。 */
    @PutMapping
    @MpPerm("dish.edit")
    public R<?> update(@RequestBody DishSaveDTO dto) {
        if (dto.getDish().getId() != null) {
            querySvc.snapshotBeforeUpdate(dto.getDish().getId());
        }
        svc.saveFull(dto);
        return R.ok(null);
    }

    @DeleteMapping("/{id}")
    public R<?> del(@PathVariable Long id) {
        svc.removeById(id);
        return R.ok(null);
    }
}
