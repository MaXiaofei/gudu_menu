package com.gudu.xsd.modules.feedback.application;

import java.time.LocalDateTime;
import java.util.List;

/** 反馈列表项（GET /feedback，超管）。type 小写 code；status NEW / REPLIED / DONE。 */
public record FeedbackView(Long id, Long memberId, String memberName, String type, String sourceRef, String content,
                           String contact, List<String> images, String clientInfo, String status, String reply,
                           LocalDateTime repliedAt, Long repliedBy, LocalDateTime createTime) {
}
