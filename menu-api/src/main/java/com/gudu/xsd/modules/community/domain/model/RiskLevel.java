package com.gudu.xsd.modules.community.domain.model;

/** 预审风险分级：LOW 直接发布；MEDIUM 先发布后复审；HIGH 进入人工队列且对他人不可见。 */
public enum RiskLevel {
    LOW, MEDIUM, HIGH;

    public static RiskLevel of(String s) {
        try {
            return s == null ? LOW : valueOf(s);
        } catch (IllegalArgumentException e) {
            return LOW;
        }
    }
}
