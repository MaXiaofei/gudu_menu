package com.gudu.xsd.modules.backoffice.domain.model;

/**
 * 账号查询条件（F.1）。
 *
 * @param keyword    昵称包含 / 账号 id 精确
 * @param openidTail openid 末尾若干位（≥4 位才生效，防止宽泛枚举）
 * @param familyId   属于该家庭
 * @param status     ACTIVE / DEACTIVATED / MERGED
 * @param bannedOnly 只看封禁中的
 */
public record AccountSearch(String keyword, String openidTail, Long familyId, String status, boolean bannedOnly) {

    public static final int OPENID_TAIL_MIN = 4;

    public AccountSearch {
        keyword = blankToNull(keyword);
        openidTail = blankToNull(openidTail);
        if (openidTail != null && openidTail.length() < OPENID_TAIL_MIN) openidTail = null;
        status = blankToNull(status);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
