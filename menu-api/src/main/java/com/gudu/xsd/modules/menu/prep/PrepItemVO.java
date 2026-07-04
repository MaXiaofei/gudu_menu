package com.gudu.xsd.modules.menu.prep;

import java.math.BigDecimal;
import java.util.List;

/**
 * 备菜列表一行（按食材聚合）：食材 + 总用量 + 来自哪些菜 + 备料状态 + 是否共用。
 *
 * <p>参照 {@link com.gudu.xsd.modules.shopping.ShoppingItemVO} VO 范式（record + 中文友好字段）。
 */
public record PrepItemVO(
        Long ingredientId,
        String ingredientName,
        /** 聚合总克数（已 ×servingFactor，不减库存）。 */
        BigDecimal totalGrams,
        /** 被几道菜用到。 */
        int dishCount,
        /** 用到该食材的菜名列表（共用高亮用，来自 #2 detail 冗余的 dishName）。 */
        List<String> dishNames,
        /** {@link PrepStatus} 名（大写）；无记录即 {@code PENDING}。 */
        String status,
        /** 是否共用项（dishCount &gt;= 2），前端 🔥 高亮便利字段。 */
        boolean shared
) {}
