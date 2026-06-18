package com.yanhuo.xsd.modules.shopping;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 采购明细 VO：在 ShoppingItem 基础上挂中文展示名。
 * 参照 PantryVO 范式。枚举铁律：前端只拿中文 name。
 *
 * <p>新设计（redesign）新增：
 * <ul>
 *   <li>referenceGrams（参考克数，前端小字灰色提示「约 500g」）；</li>
 *   <li>purchaseUnitName（采购单位中文名，join sys_dict group=purchase_unit：斤/把/个…）。</li>
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
}
