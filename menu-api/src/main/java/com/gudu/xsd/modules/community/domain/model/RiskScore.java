package com.gudu.xsd.modules.community.domain.model;

/** 预审分项得分（0–1），后台审核页展示「AI 判定」条。 */
public record RiskScore(String label, double score) {

    public RiskScore {
        score = Math.max(0, Math.min(1, score));
        score = Math.round(score * 100) / 100.0;
    }
}
