package com.yanhuo.xsd.modules.shopping;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.yanhuo.xsd.common.PageQuery;
import com.yanhuo.xsd.common.R;
import com.yanhuo.xsd.modules.member.MpPerm;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * 采购清单接口（redesign）。范式照 pantry/mealplan：返回 R<T>，@Tag 分组。
 * 不做估价。generate 走 @MpPerm(shopping.generate) 功能权限。
 *
 * <p>三数据源：menu（从 menu_dish）/ dish（多选）/ plan（从 meal_plan_item）。
 * 采购量+采购单位由用户填（PUT /item/{id}），referenceGrams 仅提示。
 */
@RestController
@RequestMapping("/shopping")
@RequiredArgsConstructor
@Tag(name = "采购清单")
public class ShoppingController {

    private final ShoppingService svc;

    /** 生成请求体：sourceType + sourceId(menu/plan) 或 sourceIds(dish 多选)。 */
    @Data
    public static class GenerateReq {
        /** 数据源：menu / dish / plan。 */
        private String sourceType;
        /** menu 或 plan 的 id（sourceType=menu/plan 时用）。 */
        private Long sourceId;
        /** dish 多选 id 列表（sourceType=dish 时用）。 */
        private List<Long> sourceIds;
    }

    /**
     * 从菜单/菜品/周计划生成采购草稿（按 ingredient_id 去重，referenceGrams = 菜谱克数合计）。
     * 返回新生成的 shopping_list.id。
     */
    @PostMapping("/generate")
    @MpPerm("shopping.generate")
    public R<Long> generate(@RequestBody GenerateReq req) {
        String type = req.getSourceType() == null ? "plan" : req.getSourceType();
        return R.ok(svc.generate(type, req.getSourceId(), req.getSourceIds()));
    }

    /** 采购清单详情：含 items（带中文：食材名/采购单位名/品类名）+ 按品类分区。 */
    @GetMapping("/{listId}")
    public R<ShoppingListVO> get(@PathVariable Long listId) {
        return R.ok(svc.getDetail(listId));
    }

    /** 采购清单分页列表（后台管理用）。 */
    @GetMapping
    public R<IPage<ShoppingList>> list(PageQuery q) {
        return R.ok(svc.page(q));
    }

    /** 用户填采购量 + 采购单位（斤/把/个 等）。 */
    @PutMapping("/item/{itemId}")
    public R<?> updatePurchase(@PathVariable Long itemId, @RequestBody UpdatePurchaseReq req) {
        svc.updatePurchase(itemId, req.getPurchaseAmount(), req.getPurchaseUnitId());
        return R.ok(null);
    }

    /** 更新采购量/单位请求体。 */
    @Data
    public static class UpdatePurchaseReq {
        private BigDecimal purchaseAmount;
        private Long purchaseUnitId;
    }

    /** 勾选/取消勾选某明细已买。 */
    @PutMapping("/item/{itemId}/purchased")
    public R<?> togglePurchased(@PathVariable Long itemId) {
        svc.togglePurchased(itemId);
        return R.ok(null);
    }

    /** 删除某明细。 */
    @DeleteMapping("/item/{itemId}")
    public R<?> delItem(@PathVariable Long itemId) {
        svc.deleteItem(itemId);
        return R.ok(null);
    }

    /** 删除整张清单。 */
    @DeleteMapping("/{listId}")
    public R<?> delList(@PathVariable Long listId) {
        svc.deleteList(listId);
        return R.ok(null);
    }

    /**
     * 手动添加自定义采购项（V30）：采购清单不强绑菜单/菜品。
     * name 命中已有 ingredient → 关联 ingredientId；未命中 → ingredientId 留空、name 存 custom_name。
     * 返回新增的 shopping_item.id。
     */
    @PostMapping("/item/custom")
    public R<Long> addCustomItem(@RequestBody AddCustomItemReq req) {
        return R.ok(svc.addItemCustom(req.getListId(), req.getName(),
                req.getAmount(), req.getUnitId(), req.getPurchaseCategoryId()));
    }

    /** 手动添加自定义采购项请求体。 */
    @Data
    public static class AddCustomItemReq {
        /** 目标采购清单 id。 */
        private Long listId;
        /** 自定义食材名（如「土豆」「老抽」）。 */
        private String name;
        /** 采购量（可空，用户后填）。 */
        private BigDecimal amount;
        /** 采购单位 sys_dict(group=purchase_unit) id（可空）。 */
        private Long unitId;
        /** 采购品类 sys_dict(group=purchase_category) id（可空，用于分区）。 */
        private Long purchaseCategoryId;
    }
}
