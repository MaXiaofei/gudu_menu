package com.gudu.xsd.modules.vision.domain.port;

/**
 * 多模态模型端口（集成阶段由模型网关 VISION 能力实现；本上下文不直接依赖 modules.ai）。
 * 未提供实现 Bean 时识别走 Mock。
 */
public interface VisionModelPort {

    /**
     * @param imageUrl   图片地址（本站上传路径，实现方负责转成模型可读的 URL / base64）
     * @param prompt     识别提示词（{@code VisionPrompts.promptOf}）
     * @param jsonSchema 输出 JSON Schema（{@code VisionPrompts.schemaOf}）
     * @return 模型原始文本输出（期望为 JSON，可能夹带代码块围栏，由解析器容错）
     */
    String recognize(String imageUrl, String prompt, String jsonSchema);
}
