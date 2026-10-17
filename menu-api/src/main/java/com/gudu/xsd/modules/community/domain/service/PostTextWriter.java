package com.gudu.xsd.modules.community.domain.service;

/**
 * 「AI 帮我写」文案生成端口。当前为模板实现 {@link TemplatePostTextWriter}；
 * 模型网关（AiClient.chat）落地后在集成阶段补 LLM 实现，失败回退模板。
 */
public interface PostTextWriter {

    /** style：default / casual / short。素材里不包含家庭成员名。 */
    String write(Material material, String style);

    /** 文案素材：菜名、用时（分钟，可空）、做菜时间描述（如「今天 18:40」，可空）、难度描述（可空）。 */
    record Material(String dishName, Integer minutes, String cookedWhen, String difficulty) {
    }
}
