package com.gudu.xsd.modules.nutrition.domain.service;

/**
 * 食材名称规整（纯函数）：去首尾空格（含全角空格）、全角括号转半角、连续空白折叠为一个空格。
 * 括号里的内容保留（公共库有「猪肉(瘦)」「粉丝(干)」这类带括号的正式名）。
 * 规则与 scripts/ingredient-alias/gen_alias_sql.py 的 norm() 保持一致。
 */
public final class IngredientNameNormalizer {

    private IngredientNameNormalizer() {
    }

    /** 规整名称；null 返回空串。 */
    public static String normalize(String raw) {
        if (raw == null) return "";
        String s = raw.replace('（', '(').replace('）', ')').replace('　', ' ');
        return s.replaceAll("\\s+", " ").trim();
    }
}
