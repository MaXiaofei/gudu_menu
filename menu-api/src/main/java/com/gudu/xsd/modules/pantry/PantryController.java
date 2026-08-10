package com.gudu.xsd.modules.pantry;

import com.gudu.xsd.common.R;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import lombok.Data;

/**
 * 食材库存接口（V42 手动 3 档版）。范式照 mealplan/ingredient：返回 R<T>，@Tag 分组。
 *
 * <p>APP 核心：grouped（三色列表）/ item（详情）/ PUT level（设档位）/ manual（入库设档位）。
 * 管理后台共用：grouped（列表）+ PUT level（改档位）+ DELETE level（删档位）。
 * expiring 为临期通知遗留接口（读 pantry 批次表）。
 */
@RestController
@RequestMapping("/pantry")
@RequiredArgsConstructor
@Tag(name = "食材库存")
public class PantryController {

    private final PantryService svc;

    /** 手动入库请求：ingredientId/name 二选一，带档位（默认 ENOUGH）与来源备注。 */
    @Data
    public static class ManualAddReq {
        private Long ingredientId;
        private String name;
        private String level;
        private String sourceNote;
    }

    /** 设档位请求：ENOUGH/LOW/NONE + 备注（可空）。 */
    @Data
    public static class LevelReq {
        private String level;
        private String note;
    }

    /**
     * 三色分组列表：按档位分 用完/不足/充足 三组 + 汇总数（APP 库存页 + 管理后台共用）。
     * 可选参数：level 按档过滤、keyword 按名匹配、pageNum/pageSize 分页（DESIGN.md §12，每页 10 条）；
     * 不传 pageSize 时全量返回（管理后台兼容）。
     */
    @GetMapping("/grouped")
    public R<PantryGroupedVO> grouped(
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer pageNum,
            @RequestParam(required = false) Integer pageSize) {
        return R.ok(svc.grouped(level, keyword, pageNum, pageSize));
    }

    /** 食材详情：当前档位 + 最近变动流水。 */
    @GetMapping("/item")
    public R<PantryItemDetailVO> itemDetail(@RequestParam Long ingredientId) {
        return R.ok(svc.itemDetail(ingredientId));
    }

    /** 设档位（手动修正 / 管理后台）：直接设 ENOUGH/LOW/NONE，记一笔 manual 流水。 */
    @PutMapping("/{ingredientId}/level")
    public R<?> setLevel(@PathVariable Long ingredientId, @RequestBody LevelReq req) {
        svc.setLevel(ingredientId, req.getLevel(), StockLog.ACTION_MANUAL, req.getNote(), null);
        return R.ok(null);
    }

    /** 删档位（管理后台删除 = 回到"没建档"）：删 ingredient_stock + 记流水。 */
    @DeleteMapping("/{ingredientId}/level")
    public R<?> deleteLevel(@PathVariable Long ingredientId) {
        svc.removeLevel(ingredientId, StockLog.ACTION_MANUAL, null, null);
        return R.ok(null);
    }

    /** 手动入库（朋友送/赠品/旧库存补登）：按名匹配/新建食材 → 设档位（默认 ENOUGH）。 */
    @PostMapping("/manual")
    public R<?> manualAdd(@RequestBody ManualAddReq req) {
        svc.manualAdd(req.getIngredientId(), req.getName(), req.getLevel(), req.getSourceNote());
        return R.ok(null);
    }

    /** 临期库存：过期日在 [today, today+days] 内的（临期通知遗留接口，读 pantry 批次表）。 */
    @GetMapping("/expiring")
    public R<List<PantryVO>> expiring(@RequestParam(defaultValue = "3") int days) {
        return R.ok(svc.listExpiring(days));
    }
}
