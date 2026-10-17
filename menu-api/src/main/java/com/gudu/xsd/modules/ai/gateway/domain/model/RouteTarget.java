package com.gudu.xsd.modules.ai.gateway.domain.model;

/** 路由目标：某供应商的某模型。 */
public record RouteTarget(Long providerId, String model) {

    public RouteTarget {
        if (providerId == null) throw new IllegalArgumentException("providerId 不能为空");
        if (model == null || model.isBlank()) throw new IllegalArgumentException("model 不能为空");
        model = model.trim();
    }
}
