package com.gudu.xsd.modules.pantry;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gudu.xsd.common.PageQuery;
import com.gudu.xsd.common.R;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import lombok.Data;

/**
 * 食材库存接口。范式照 mealplan/ingredient：返回 R<T>，@Tag 分组。
 */
@RestController
@RequestMapping("/pantry")
@RequiredArgsConstructor
@Tag(name = "食材库存")
public class PantryController {

    private final PantryService svc;

    @Data
    public static class BatchItem {
        private String name;
        private BigDecimal amount;
        private String unit;
        private LocalDate expireDate;
    }

    @Data
    public static class DeductReq {
        private BigDecimal amount;
    }

    /** 盘点请求：传实际数量（目标值，按食材默认单位）。 */
    @Data
    public static class AdjustReq {
        private Long ingredientId;
        private BigDecimal newAmount;
        private String sourceNote;
    }

    /** 手动添加请求：ingredientId/name 二选一，带来源备注。 */
    @Data
    public static class ManualAddReq {
        private Long ingredientId;
        private String name;
        private BigDecimal amount;
        private Long unitId;
        private String sourceNote;
        private LocalDate expireDate;
    }

    /** 手动扣减库存 */
    @PostMapping("/{id}/deduct")
    public R<Map<String, Object>> deduct(@PathVariable Long id, @RequestBody DeductReq req) {
        BigDecimal remain = svc.deduct(id, req.getAmount());
        return R.ok(Map.of("remain", remain));
    }

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

    /** 不足库存：余量低于阈值的（按食材聚合判，V39）。 */
    @GetMapping("/low")
    public R<List<PantryGroupedVO.Item>> low() {
        return R.ok(svc.listLow());
    }

    // ===================== 库存页主页（三色分组，V39） =====================

    /** 三色分组列表：按食材聚合，分 够/低/缺 三组 + 汇总数。 */
    @GetMapping("/grouped")
    public R<PantryGroupedVO> grouped() {
        return R.ok(svc.grouped());
    }

    /** 食材详情：合计 + 阈值克数 + 最近 6 条变动流水。 */
    @GetMapping("/item")
    public R<PantryItemDetailVO> itemDetail(@RequestParam Long ingredientId) {
        return R.ok(svc.itemDetail(ingredientId));
    }

    /** 盘点：传实际数量，后端算差额并记一笔 source=inventory 流水。 */
    @PostMapping("/adjust")
    public R<?> adjust(@RequestBody AdjustReq req) {
        svc.adjust(req.getIngredientId(), req.getNewAmount(), req.getSourceNote());
        return R.ok(null);
    }

    /** 手动添加：新增一笔带「手动」来源标签的库存（别人送/赠品/旧库存补登）。 */
    @PostMapping("/manual")
    public R<?> manualAdd(@RequestBody ManualAddReq req) {
        svc.manualAdd(req.getIngredientId(), req.getName(), req.getAmount(),
                req.getUnitId(), req.getSourceNote(), req.getExpireDate());
        return R.ok(null);
    }

    /** 新增库存。 */
    @PostMapping
    public R<Long> add(@RequestBody Pantry pantry) {
        svc.saveWithGrams(pantry);
        return R.ok(pantry.getId());
    }

    /** 批量添加：按名称匹配食材，未匹配则自动创建食材。返回成功条数。 */
    @PostMapping("/batch")
    public R<Map<String, Object>> batchAdd(@RequestBody List<BatchItem> items) {
        int count = svc.saveBatch(items);
        return R.ok(Map.of("count", count));
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
