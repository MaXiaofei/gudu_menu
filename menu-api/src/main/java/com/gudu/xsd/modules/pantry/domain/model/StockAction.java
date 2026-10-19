package com.gudu.xsd.modules.pantry.domain.model;

/** 档位变动原因（stock_log.action 取值）。 */
public enum StockAction {
    /** 做菜确认·用完了。 */
    COOK("cook"),
    /** 做菜确认·用了一些。 */
    COOK_PARTIAL("cook_partial"),
    /** 采购勾选已买入库。 */
    PURCHASE("purchase"),
    /** 手动修正/入库。 */
    MANUAL("manual");

    private final String code;

    StockAction(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
