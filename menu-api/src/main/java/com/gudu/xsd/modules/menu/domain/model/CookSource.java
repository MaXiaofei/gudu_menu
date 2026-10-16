package com.gudu.xsd.modules.menu.domain.model;

/** 做菜来源（cooking_record.source 取值，与 foodlog 筛选一致：menu / dish）。 */
public enum CookSource {
    /** 整集做（食集）。 */
    MENU("menu"),
    /** 单菜直做。 */
    DISH("dish");

    private final String code;

    CookSource(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
