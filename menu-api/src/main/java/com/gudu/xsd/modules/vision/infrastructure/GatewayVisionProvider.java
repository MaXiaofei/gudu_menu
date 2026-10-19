package com.gudu.xsd.modules.vision.infrastructure;

import com.gudu.xsd.modules.vision.domain.model.RecognitionResult;
import com.gudu.xsd.modules.vision.domain.model.VisionMode;
import com.gudu.xsd.modules.vision.domain.port.VisionModelPort;
import com.gudu.xsd.modules.vision.domain.port.VisionProvider;
import com.gudu.xsd.modules.vision.domain.service.VisionPrompts;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 经模型网关 VISION 能力识别：提示词与 Schema 取 {@link VisionPrompts}，返回文本交给
 * {@link VisionOutputParser} 健壮解析。{@link VisionModelPort} 由集成阶段的网关适配器提供；
 * 未提供时 {@link #available()} 为 false，路由退回 Mock。
 */
@Component
public class GatewayVisionProvider implements VisionProvider {

    private final ObjectProvider<VisionModelPort> modelPort;

    public GatewayVisionProvider(ObjectProvider<VisionModelPort> modelPort) {
        this.modelPort = modelPort;
    }

    public boolean available() {
        return modelPort.getIfAvailable() != null;
    }

    @Override
    public RecognitionResult recognize(VisionMode mode, String imageUrl) {
        VisionModelPort port = modelPort.getIfAvailable();
        if (port == null) throw new IllegalStateException("模型网关未接入视觉能力");
        VisionModelPort.Output out = port.recognize(imageUrl, VisionPrompts.promptOf(mode), VisionPrompts.schemaOf(mode));
        RecognitionResult parsed = VisionOutputParser.parse(out.text(), mode);
        return out.degraded() ? parsed.asDegraded() : parsed;
    }

    @Override
    public String name() {
        return "gateway";
    }
}
