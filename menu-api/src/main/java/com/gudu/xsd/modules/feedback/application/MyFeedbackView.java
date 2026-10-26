package com.gudu.xsd.modules.feedback.application;

import java.time.LocalDateTime;
import java.util.List;

/** 我的反馈（GET /feedback/mine、/feedback/mine/{id}，提交人本人）：不含联系方式与设备信息。 */
public record MyFeedbackView(Long id, String type, String sourceRef, String content, List<String> images,
                             String status, String reply, LocalDateTime repliedAt, LocalDateTime createTime) {
}
