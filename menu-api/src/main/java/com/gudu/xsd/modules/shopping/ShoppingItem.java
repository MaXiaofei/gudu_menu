package com.gudu.xsd.modules.shopping;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 采购明细项：每行 = 某食材的采购草稿行。
 *
 * <p>新设计（redesign）字段语义：
 * <ul>
 *   <li>ingredientId：食材（按 ingredient_id 去重生成）；</li>
 *   <li>referenceGrams：参考克数（菜谱用量合计，信息性提示，可空）；</li>
 *   <li>purchaseAmount：用户填的采购量（如 2 表示 2 斤），可空；</li>
 *   <li>purchaseUnitId：采购单位 sys_dict(group=purchase_unit) id，可空；</li>
 *   <li>totalAmount / unitId / purchaseCategoryId：保留（参考用，不删），新代码读 referenceGrams。</li>
 * </ul>
 */
@Data
@TableName("shopping_item")
public class ShoppingItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long listId;

    private Long ingredientId;

    /** 合并后总量（旧语义，保留不删）。 */
    private BigDecimal totalAmount;

    /** 菜的克单位 sys_dict(group=unit)（旧语义，保留不删）。 */
    private Long unitId;

    /** 采购品类 sys_dict(group=purchase_category)（参考分区用）。 */
    private Long purchaseCategoryId;

    /** 用户填的采购量（如 2 表示 2 斤），可空（草稿态）。 */
    private BigDecimal purchaseAmount;

    /** 采购单位 sys_dict(group=purchase_unit) id（斤/把/个 等），可空（草稿态）。 */
    private Long purchaseUnitId;

    /** 参考克数：菜谱用量合计，信息性提示，可空。 */
    private BigDecimal referenceGrams;

    /** 是否已买（0 未买 / 1 已买）。 */
    private Integer purchased;

    /**
     * 自定义食材名（V30）：手动添加采购项时，若 name 未命中 ingredient 表，
     * ingredientId 留空，食材名直接存这里。命中时此列为空（用 ingredientName 展示）。
     */
    private String customName;
}
