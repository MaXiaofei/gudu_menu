package com.gudu.xsd.modules.feedback.application;

import java.util.List;

/**
 * 提交反馈入参。
 *
 * @param type      general / ai / complaint，空按 general
 * @param sourceRef 来源引用（AI 内容反馈带 {@code agent:消息id} 之类；投诉带来源页面），可空
 */
public record FeedbackSubmitCommand(String content, String contact, List<String> images, String clientInfo,
                                    String type, String sourceRef) {
}
