package com.gudu.xsd.modules.vision.infrastructure;

import com.gudu.xsd.modules.vision.domain.model.RecognitionResult;
import com.gudu.xsd.modules.vision.domain.model.VisionMode;
import com.gudu.xsd.modules.vision.domain.port.VisionProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/** 识别路由（应用层注入的唯一实现）：网关视觉能力可用则走网关，否则 Mock。 */
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
        return current().recognize(mode, imageUrl);
    }

    @Override
    public String name() {
        return current().name();
    }

    private VisionProvider current() {
        return gateway.available() ? gateway : mock;
    }
}
