package com.gudu.xsd.modules.community.domain.service;

/** 举报策略：不同用户举报数达到阈值即自动隐藏（同一用户重复举报只算一次，由唯一键保证）。 */
public final class ReportPolicy {

    public static final int AUTO_HIDE_THRESHOLD = 3;

    private ReportPolicy() {
    }

    public static boolean shouldAutoHide(long distinctReporters) {
        return distinctReporters >= AUTO_HIDE_THRESHOLD;
    }
}
