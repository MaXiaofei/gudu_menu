package com.gudu.xsd.modules.community.domain.service;

import com.gudu.xsd.modules.community.domain.model.ModerationVerdict;

import java.util.List;

/**
 * 内容预审端口：输入标题/正文/图片，输出风险分级。
 * 当前实现为规则版 {@link RulePostModerator}；LLM / 图片标签预审在模型网关接入后另提供实现（可异步）。
 */
public interface PostModerator {

    /** 发布前同步检查：命中禁发内容直接抛 DomainException 拒绝。 */
    void assertPublishable(String title, String text);

    ModerationVerdict review(String title, String text, List<String> images);
}
