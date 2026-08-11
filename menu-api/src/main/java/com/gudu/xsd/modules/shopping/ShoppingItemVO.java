package com.gudu.xsd.modules.shopping;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 采购明细 VO：在 ShoppingItem 基础上挂中文展示名。
 * 参照 PantryVO 范式。枚举铁律：前端只拿中文 name。
 *
 * <p>V55（食材去单位）：unitName/convertConfigured/pantryGrams/shortageGrams 字段
 * 随换算与余色链路删除；采购量展示用 purchaseAmount + purchaseUnitName。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ShoppingItemVO extends ShoppingItem {

    /** 食材名（join ingredient；手动加项取 customName）。 */
    private String ingredientName;

    /** 采购品类名（join sys_dict group=purchase_category，参考分区用）。 */
    private String purchaseCategoryName;

    /** 采购单位中文名（join sys_dict group=purchase_unit：斤/把/个…），枚举铁律。 */
    private String purchaseUnitName;

    /** 三色状态：RED_NONE 没有 / YELLOW_SHORT 差 / GREEN_ENOUGH 够；customName 项或无档位为 null（前端标灰）。 */
    private String stockStatus;
}
