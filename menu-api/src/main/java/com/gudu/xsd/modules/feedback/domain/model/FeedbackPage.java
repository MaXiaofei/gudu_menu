package com.gudu.xsd.modules.feedback.domain.model;

import java.util.List;

/** 分页结果（领域读模型）。 */
public record FeedbackPage(List<Feedback> records, long total, int pageNum, int pageSize) {
}
