package com.gudu.xsd.modules.shopping;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;
import java.util.Map;

/**
 * 采购清单详情 VO：清单元信息 + 全部明细（带中文）+ 按品类分区视图。
 * items 为全部明细（平铺）；grouped 为按 purchaseCategoryId 分区的同 items 列表，key 为品类 id（可空）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ShoppingListVO extends ShoppingList {

    /** 全部明细（带中文，平铺）。 */
    private List<ShoppingItemVO> items;

    /** 按采购品类分区的明细（key=品类id，可 null；value=该品类下的明细）。 */
    private Map<Long, List<ShoppingItemVO>> grouped;

    /** 各品类的中文名映射（key=品类id，可 null；value=品类名，便于前端展示分区标题）。 */
    private Map<Long, String> categoryNames;
}
