package com.gudu.xsd.modules.mealplan.domain.model;

import java.util.EnumSet;
import java.util.Set;

/** 格子角标：EXPIRING 用掉快到期的；BABY 宝宝可吃（辅食版）。 */
public enum SlotBadge {
    EXPIRING, BABY;

    /** 逗号分隔存储格式解析（未知值忽略）。 */
    public static Set<SlotBadge> parseCsv(String csv) {
        Set<SlotBadge> out = EnumSet.noneOf(SlotBadge.class);
        if (csv == null || csv.isBlank()) return out;
        for (String s : csv.split(",")) {
            for (SlotBadge b : values()) {
                if (b.name().equals(s.trim())) out.add(b);
            }
        }
        return out;
    }

    public static String toCsv(Set<SlotBadge> badges) {
        if (badges == null || badges.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (SlotBadge b : values()) {
            if (badges.contains(b)) sb.append(sb.length() == 0 ? "" : ",").append(b.name());
        }
        return sb.toString();
    }
}
