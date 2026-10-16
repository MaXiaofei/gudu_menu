package com.gudu.xsd.modules.feedback.application;

import java.time.LocalDateTime;
import java.util.List;

/** 反馈列表项（GET /feedback，超管）。 */
public record FeedbackView(Long id, Long memberId, String memberName, String content, String contact,
                           List<String> images, String clientInfo, String status, LocalDateTime createTime) {
}
