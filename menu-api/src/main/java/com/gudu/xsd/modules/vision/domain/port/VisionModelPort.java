package com.gudu.xsd.modules.vision.domain.port;

/**
 * 多模态模型端口：由模型网关 VISION 能力实现（{@code GatewayVisionModelAdapter}）；本上下文领域层不依赖 modules.ai。
 * 真实模型全部不可用时抛异常，由识别路由退回 Mock 示例并标记降级。
 */
public interface VisionModelPort {

    /**
     * @param imageUrl   图片地址（本站上传路径或公网 URL，实现方负责转成模型可读的 URL / base64）
     * @param prompt     识别提示词（{@code VisionPrompts.promptOf}）
     * @param jsonSchema 输出 JSON Schema（{@code VisionPrompts.schemaOf}）
     * @return 模型原始文本输出（期望为 JSON，可能夹带代码块围栏，由解析器容错）与是否降级
     */
    Output recognize(String imageUrl, String prompt, String jsonSchema);

    /**
     * @param text     模型原始输出
     * @param degraded 未用路由主目标（用了后备模型）
     * @param model    实际生效的模型名（日志用）
     */
    record Output(String text, boolean degraded, String model) {
    }
}
