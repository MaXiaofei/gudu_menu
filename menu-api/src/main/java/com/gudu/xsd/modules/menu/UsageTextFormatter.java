package com.gudu.xsd.modules.menu;

import java.math.BigDecimal;

/**
 * 用量原文格式化（V55 备菜/做菜确认通用）：把「菜名 + 用量数字 + 单位 + 份数」拼成展示文本。
 *
 * <p>规则：
 * <ul>
 *   <li>amount 为 null → 返回 null（调用方过滤掉，不展示该笔）；</li>
 *   <li>菜名 null → 兜底「菜#dishId」；</li>
 *   <li>单位 null/空 → 不拼单位（只显数字）；</li>
 *   <li>份数 &gt; 1 → 追加「×N」（stripTrailingZeros 去尾零，1.50→1.5）；</li>
 *   <li>数字 stripTrailingZeros：2.00→2，2.50→2.5。</li>
 * </ul>
 *
 * <p>抽成纯函数便于单测；CookService（做菜确认弹窗）与 MenuPrepService（备菜 Tab）共用。
 */
public final class UsageTextFormatter {

    private UsageTextFormatter() {}

    /**
     * @param dishId        菜 id（菜名缺失时兜底「菜#id」展示）
     * @param dishName      菜名（可空）
     * @param amount        用量数字（null → 返回 null）
     * @param unitName      单位名（可空，空串视同 null）
     * @param servingFactor 份数（null 或 ≤1 不拼 ×N）
     * @return 用量原文，如「番茄炒蛋 2个 ×3」；amount 为 null 返回 null
     */
    public static String format(Long dishId, String dishName, BigDecimal amount,
                                String unitName, BigDecimal servingFactor) {
        if (amount == null) return null;
        String head = dishName != null ? dishName : ("菜#" + dishId);
        StringBuilder sb = new StringBuilder(head).append(' ')
                .append(amount.stripTrailingZeros().toPlainString());
        if (unitName != null && !unitName.isBlank()) sb.append(unitName);
        if (servingFactor != null && servingFactor.compareTo(BigDecimal.ONE) > 0) {
            sb.append(" ×").append(servingFactor.stripTrailingZeros().toPlainString());
        }
        return sb.toString();
    }
}
