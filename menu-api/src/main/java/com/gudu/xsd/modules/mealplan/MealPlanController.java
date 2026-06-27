package com.gudu.xsd.modules.mealplan;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gudu.xsd.common.PageQuery;
import com.gudu.xsd.common.R;
import com.gudu.xsd.modules.member.MpPerm;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 周计划接口。范式照 cookbook/menu：返回 R<T>，@Tag 分组。
 */
@RestController
@RequestMapping("/mealplan")
@RequiredArgsConstructor
@Tag(name = "周计划")
public class MealPlanController {

    private final MealPlanService svc;

    /** 周计划列表（后台管理用，分页）。 */
    @GetMapping
    public R<IPage<MealPlan>> list(PageQuery q) {
        return R.ok(svc.page(q));
    }

    /** 创建周计划。 */
    @PostMapping
    @MpPerm("menu.plan")
    public R<Long> create(@RequestBody CreatePlanReq req) {
        return R.ok(svc.createPlan(req.weekStart(), req.name()));
    }

    /** 查周计划（含 items）。 */
    @GetMapping("/{planId}")
    public R<MealPlanService.PlanDetail> get(@PathVariable Long planId) {
        return R.ok(svc.getPlan(planId));
    }

    /**
     * 添加/更新一个排菜项。
     * 返回当前 plan 下与该 item 同日同餐的重复提示（detectDuplicates 纯函数结果）。
     */
    @PostMapping("/{planId}/item")
    @MpPerm("menu.plan")
    public R<Map<String, Object>> addItem(@PathVariable Long planId, @RequestBody MealPlanItem item) {
        item.setPlanId(planId);
        List<MealPlanService.Item> dup = svc.saveItem(item);
        return R.ok(Map.of(
                "itemId", item.getId(),
                "duplicates", dup));
    }

    /** 删排菜项。 */
    @DeleteMapping("/item/{itemId}")
    public R<?> delItem(@PathVariable Long itemId) {
        svc.deleteItem(itemId);
        return R.ok(null);
    }

    /** 套用模板：把 template.snapshot 批量插入到 plan。 */
    @PostMapping("/{planId}/apply-template")
    public R<Integer> applyTemplate(@PathVariable Long planId,
                                    @RequestParam Long templateId) {
        return R.ok(svc.applyTemplate(templateId, planId));
    }

    /** 模板列表。 */
    @GetMapping("/templates")
    public R<List<MenuTemplate>> templates() {
        return R.ok(svc.listTemplates());
    }

    /** 保存模板（新增/更新）。 */
    @PostMapping("/templates")
    public R<Long> saveTemplate(@RequestBody MenuTemplate t) {
        return R.ok(svc.saveTemplate(t));
    }

    /** 创建周计划请求体。 */
    public record CreatePlanReq(
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
            String name) {}
}
