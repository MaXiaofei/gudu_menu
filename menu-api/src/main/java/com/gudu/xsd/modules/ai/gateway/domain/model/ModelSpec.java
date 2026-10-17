package com.gudu.xsd.modules.ai.gateway.domain.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * 供应商下的一个模型。
 *
 * @param peakHours        高峰时段（null = 全天同价）
 * @param supportsThinking 是否接受 thinking / reasoning_effort 参数
 */
public record ModelSpec(String model, Set<Capability> capabilities, ModelPrice price, PeakHours peakHours,
                        boolean supportsThinking) {

    public ModelSpec {
        if (model == null || model.isBlank()) throw new IllegalArgumentException("模型名不能为空");
        model = model.trim();
        capabilities = capabilities == null || capabilities.isEmpty()
                ? EnumSet.noneOf(Capability.class) : EnumSet.copyOf(capabilities);
        price = price == null ? ModelPrice.FREE : price;
    }

    public boolean supports(Capability c) {
        return capabilities.contains(c);
    }
}
