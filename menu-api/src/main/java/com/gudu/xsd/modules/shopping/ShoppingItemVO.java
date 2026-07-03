package com.gudu.xsd.modules.shopping;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 采购明细 VO：在 ShoppingItem 基础上挂中文展示名。
 * 参照 PantryVO 范式。枚举铁律：前端只拿中文 name。
 *
 * <p>新设计（redesign）新增：
 * <ul>
 *   <li>referenceGrams（参考克数，前端小字灰色提示「约 500g」）；</li>
 *   <li>purchaseUnitName（采购单位中文名，join sys_dict group=purchase_unit：斤/把/个…）。</li>
 * </ul>
 *
 * <p>Plan B 三色余色新增：
 * <ul>
 *   <li>pantryGrams（家中现有克数，pantry 按 ingredient_id 聚合）；</li>
 *   <li>stockStatus（{@code RED_NONE/YELLOW_SHORT/GREEN_ENOUGH}，customName 手动加项或无用量为 null）；</li>
 *   <li>shortageGrams（差多少克，RED=needGrams、YELLOW=need-have、GREEN=0）。</li>
 * </ul>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ShoppingItemVO extends ShoppingItem {

    /** 食材名（join ingredient）。 */
    private String ingredientName;

    /** 单位名（join sys_dict group=unit，菜本身的克单位，参考）。 */
    private String unitName;

    /** 采购品类名（join sys_dict group=purchase_category，参考分区用）。 */
    private String purchaseCategoryName;

    /** 采购单位中文名（join sys_dict group=purchase_unit：斤/把/个…），枚举铁律。 */
    private String purchaseUnitName;

    /** 该项用量是否已配置换算（false=未配置，前端标灰提示）。 */
    private Boolean convertConfigured;

    /** 家中现有克数（pantry 按 ingredient_id 聚合 grams）；customName 项为 null。 */
    private BigDecimal pantryGrams;

    /** 三色状态：RED_NONE 没有 / YELLOW_SHORT 差 X / GREEN_ENOUGH 够；customName 项或无用量为 null（前端标灰）。 */
    private String stockStatus;

    /** 差多少克（RED=needGrams、YELLOW=need-have、GREEN=0）。 */
    private BigDecimal shortageGrams;
}
