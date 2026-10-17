package com.gudu.xsd.modules.community.domain.model;

import java.util.List;

/** 预审结论：风险等级 + 命中原因（展示给运营）+ 分项得分。 */
public record ModerationVerdict(RiskLevel risk, List<String> reasons, List<RiskScore> scores) {

    public ModerationVerdict {
        risk = risk == null ? RiskLevel.LOW : risk;
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
        scores = scores == null ? List.of() : List.copyOf(scores);
    }

    public static ModerationVerdict low() {
        return new ModerationVerdict(RiskLevel.LOW, List.of(), List.of());
    }
}
