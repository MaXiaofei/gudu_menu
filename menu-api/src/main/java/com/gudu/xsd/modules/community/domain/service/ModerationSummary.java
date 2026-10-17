package com.gudu.xsd.modules.community.domain.service;

import com.gudu.xsd.modules.community.domain.model.ModerationState;
import com.gudu.xsd.modules.community.domain.model.RiskLevel;

/** 预审结论的一句话摘要（后台审核页「AI 判定」）。 */
public final class ModerationSummary {

    private ModerationSummary() {
    }

    public static String of(ModerationState s, long reportCount) {
        if (s == null) return "";
        String level = switch (s.risk()) {
            case HIGH -> "高风险";
            case MEDIUM -> "中风险";
            case LOW -> "低风险";
        };
        StringBuilder sb = new StringBuilder();
        if (s.reasons().isEmpty()) {
            sb.append("未命中规则，判定").append(level);
        } else {
            sb.append("命中 ").append(String.join("、", s.reasons())).append("，判定").append(level);
        }
        if (reportCount > 0) sb.append("；另有 ").append(reportCount).append(" 人举报");
        String advice = s.risk() == RiskLevel.HIGH || reportCount >= ReportPolicy.AUTO_HIDE_THRESHOLD ? "建议隐藏"
                : s.risk() == RiskLevel.MEDIUM || reportCount > 0 ? "建议人工复核" : "可直接通过";
        return sb.append("，").append(advice).append("。").toString();
    }
}
