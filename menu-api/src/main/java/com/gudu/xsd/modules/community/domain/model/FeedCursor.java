package com.gudu.xsd.modules.community.domain.model;

import com.gudu.xsd.common.domain.DomainException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Feed 游标（值对象）：(score, id) 定位上一页最后一条 + asOf 冻结时间点。
 * <ul>
 *   <li>按 (score desc, id desc) 排序，下一页取「严格排在游标之后」的条目，不用 offset；</li>
 *   <li>asOf 之后发布的新帖不进入本轮翻页（下拉刷新才出现），保证翻页期间不重复、不遗漏；</li>
 *   <li>热度分随时间衰减，统一按 asOf 计算，跨页可比。</li>
 * </ul>
 * 编码为 URL 安全 Base64，对客户端不透明。
 */
public record FeedCursor(double score, long id, long asOfEpochMs) {

    public String encode() {
        String raw = score + "|" + id + "|" + asOfEpochMs;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static FeedCursor decode(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            String raw = new String(Base64.getUrlDecoder().decode(s), StandardCharsets.UTF_8);
            String[] p = raw.split("\\|");
            return new FeedCursor(Double.parseDouble(p[0]), Long.parseLong(p[1]), Long.parseLong(p[2]));
        } catch (RuntimeException e) {
            throw new DomainException("游标不合法，请刷新");
        }
    }

    /** 条目 (score, id) 是否严格排在游标之后（降序）。 */
    public boolean isAfter(double otherScore, long otherId) {
        int c = Double.compare(otherScore, score);
        return c < 0 || (c == 0 && otherId < id);
    }
}
