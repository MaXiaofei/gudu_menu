package com.gudu.xsd.modules.vision.infrastructure;

import com.gudu.xsd.modules.vision.domain.model.RecognitionResult;
import com.gudu.xsd.modules.vision.domain.model.VisionMode;
import com.gudu.xsd.modules.vision.domain.port.VisionProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 识别路由（应用层注入的唯一实现）：网关视觉能力可用则走网关；网关未接入、真实模型全部失败或输出无法解析时
 * 退回 Mock 示例，结果标记 degraded（客户端提示「示例结果，请核对」）。
 */
@Slf4j
@Component
@Primary
public class RoutingVisionProvider implements VisionProvider {

    private final GatewayVisionProvider gateway;
    private final MockVisionProvider mock;

    public RoutingVisionProvider(GatewayVisionProvider gateway, MockVisionProvider mock) {
        this.gateway = gateway;
        this.mock = mock;
    }

    @Override
    public RecognitionResult recognize(VisionMode mode, String imageUrl) {
        if (gateway.available()) {
            try {
                return gateway.recognize(mode, imageUrl);
            } catch (RuntimeException e) {
                log.warn("视觉模型不可用，退回示例识别 mode={}: {}", mode, e.getMessage());
            }
        }
        return mock.recognize(mode, imageUrl).asDegraded();
    }

    @Override
    public String name() {
        return gateway.available() ? gateway.name() : mock.name();
    }
}
