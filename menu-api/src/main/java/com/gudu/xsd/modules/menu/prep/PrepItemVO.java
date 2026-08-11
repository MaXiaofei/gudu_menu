package com.gudu.xsd.modules.menu.prep;

import java.util.List;

/**
 * 备菜列表一行（按食材聚合）：食材 + 用量原文明细 + 来自哪些菜 + 备料状态 + 是否共用。
 *
 * <p>参照 {@link com.gudu.xsd.modules.shopping.ShoppingItemVO} VO 范式（record + 中文友好字段）。
 *
 * <p>V55（食材去单位）：totalGrams 改为 usageTexts —— 每道菜用量原文
 * （如「番茄炒蛋 2个」「蛋花汤 3个」），不再按克汇总。
 */
public record PrepItemVO(
        Long ingredientId,
        String ingredientName,
        /** 用量原文明细（已拼菜名，如「番茄炒蛋 2个」；份数>1 时「2个 ×3」）。 */
        List<String> usageTexts,
        /** 被几道菜用到。 */
        int dishCount,
        /** 用到该食材的菜名列表（共用高亮用，来自 #2 detail 冗余的 dishName）。 */
        List<String> dishNames,
        /** {@link PrepStatus} 名（大写）；无记录即 {@code PENDING}。 */
        String status,
        /** 是否共用项（dishCount &gt;= 2），前端 🔥 高亮便利字段。 */
        boolean shared,
        /** 库存档位 ENOUGH/LOW/NONE（家里：充足/不足/用完；没建档按 NONE）。B5。 */
        String stockLevel
) {}
