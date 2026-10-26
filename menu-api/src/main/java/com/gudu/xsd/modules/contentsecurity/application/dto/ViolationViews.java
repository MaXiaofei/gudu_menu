package com.gudu.xsd.modules.contentsecurity.application.dto;

import java.time.LocalDateTime;
import java.util.List;

/** 内容安全违规复核视图（F.3 运营后台）。 */
public final class ViolationViews {

    private ViolationViews() {
    }

    /**
     * 图片违规。
     *
     * @param accountId    上传账号
     * @param purpose      用途（AVATAR / POST / UPLOAD …）
     * @param label        微信命中标签
     * @param reviewStatus null 待复核 / CONFIRMED / RESTORED
     */
    public record MediaViolation(Long id, String mediaUrl, Long accountId, String purpose, String status, int label,
                                 LocalDateTime createTime, String reviewStatus, Long reviewedBy,
                                 LocalDateTime reviewedAt) {
    }

    /**
     * 文本违规。
     *
     * @param source  结论来源 RULE / WX / RULE_FALLBACK
     * @param excerpt 内容摘录（前 100 字）
     */
    public record TextViolation(Long id, Long accountId, String purpose, String suggest, int label, String source,
                                String excerpt, LocalDateTime createdAt, boolean confirmed, Long reviewedBy,
                                LocalDateTime reviewedAt) {
    }

    public record Page<T>(List<T> records, long total) {
    }

    /** 恢复结果：filesRestored = 隔离文件是否还原（隔离目录里没有时为 false，记录仍改判）。 */
    public record RestoreResult(MediaViolation violation, boolean filesRestored) {
    }
}
