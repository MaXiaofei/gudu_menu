package com.gudu.xsd.modules.community.domain.model;

import java.util.List;

/** 帖子当前的审核状态快照：风险、原因、分项得分、是否已人工复核。 */
public record ModerationState(RiskLevel risk, List<String> reasons, List<RiskScore> scores, boolean reviewed) {

    public static final ModerationState PENDING = new ModerationState(RiskLevel.LOW, List.of(), List.of(), false);

    public ModerationState {
        risk = risk == null ? RiskLevel.LOW : risk;
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
        scores = scores == null ? List.of() : List.copyOf(scores);
    }

    public ModerationState markReviewed() {
        return new ModerationState(risk, reasons, scores, true);
    }

    public ModerationState backToQueue() {
        return new ModerationState(risk, reasons, scores, false);
    }
}
