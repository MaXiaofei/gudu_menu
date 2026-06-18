package com.yanhuo.xsd.modules.pantry;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.yanhuo.xsd.common.PageQuery;
import com.yanhuo.xsd.common.R;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 食材库存接口。范式照 mealplan/ingredient：返回 R<T>，@Tag 分组。
 */
@RestController
@RequestMapping("/pantry")
@RequiredArgsConstructor
@Tag(name = "食材库存")
public class PantryController {

    private final PantryService svc;

    /** 库存分页列表（后台管理用）。 */
    @GetMapping
    public R<IPage<PantryVO>> list(PageQuery q) {
        return R.ok(svc.page(q));
    }

    /** 临期库存：过期日在 [today, today+days] 内的。 */
    @GetMapping("/expiring")
    public R<List<PantryVO>> expiring(@RequestParam(defaultValue = "3") int days) {
        return R.ok(svc.listExpiring(days));
    }

    /** 不足库存：余量低于阈值的。 */
    @GetMapping("/low")
    public R<List<PantryVO>> low() {
        return R.ok(svc.listLow());
    }

    /** 新增库存。 */
    @PostMapping
    public R<Long> add(@RequestBody Pantry pantry) {
        svc.save(pantry);
        return R.ok(pantry.getId());
    }

    /** 更新库存。 */
    @PutMapping
    public R<?> update(@RequestBody Pantry pantry) {
        svc.updateById(pantry);
        return R.ok(null);
    }

    /** 删除库存。 */
    @DeleteMapping("/{id}")
    public R<?> del(@PathVariable Long id) {
        svc.removeById(id);
        return R.ok(null);
    }
}
