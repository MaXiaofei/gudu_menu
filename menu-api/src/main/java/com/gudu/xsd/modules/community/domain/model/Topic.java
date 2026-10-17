package com.gudu.xsd.modules.community.domain.model;

import java.time.LocalDate;

/**
 * 话题（值对象，运营维护，只读）。challenge=true 为周挑战：周期按 {@link ChallengeWeek} 周一至周日滚动；
 * startDate/endDate 为挑战总体起止（endDate 为空 = 长期）。
 */
public record Topic(Long id, String name, String intro, String rule, boolean challenge,
                    LocalDate startDate, LocalDate endDate, int sort) {

    public boolean activeOn(LocalDate d) {
        if (d == null) return false;
        if (startDate != null && d.isBefore(startDate)) return false;
        return endDate == null || !d.isAfter(endDate);
    }
}
