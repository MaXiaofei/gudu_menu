package com.gudu.xsd.modules.pantry.domain.model;

import com.gudu.xsd.common.domain.DomainException;

/** 余量三档（值对象）：ENOUGH 充足 / LOW 快用完 / NONE 没有。 */
public enum StockLevel {
    ENOUGH, LOW, NONE;

    /** 解析档位字符串；非法值抛领域异常。 */
    public static StockLevel of(String value) {
        if (value != null) {
            for (StockLevel l : values()) {
                if (l.name().equals(value)) return l;
            }
        }
        throw new DomainException("库存档位不合法");
    }

    /** 宽松解析（读库用）：null/未知 → NONE。 */
    public static StockLevel parseOrNone(String value) {
        if (value == null) return NONE;
        for (StockLevel l : values()) {
            if (l.name().equals(value)) return l;
        }
        return NONE;
    }

    /** 家里还有（够或快用完）——覆盖率按此计入「够」。 */
    public boolean isAvailable() {
        return this != NONE;
    }

    /** 「用了一些」后的档位：充足降为快用完；快用完/没有不再降（不会因用了一些自动变成用完）。 */
    public StockLevel afterPartialUse() {
        return this == ENOUGH ? LOW : this;
    }
}
