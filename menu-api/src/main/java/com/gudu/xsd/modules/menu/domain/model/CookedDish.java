package com.gudu.xsd.modules.menu.domain.model;

import com.gudu.xsd.common.domain.DomainException;

import java.math.BigDecimal;

/** 本次做的一道菜 + 份数（值对象）。 */
public record CookedDish(Long dishId, BigDecimal servingFactor) {

    public CookedDish {
        if (dishId == null) throw new DomainException("菜谱 id 不能为空");
    }
}
