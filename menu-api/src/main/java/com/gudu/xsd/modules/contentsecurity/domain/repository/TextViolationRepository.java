package com.gudu.xsd.modules.contentsecurity.domain.repository;

import com.gudu.xsd.modules.contentsecurity.domain.model.TextViolation;

import java.util.List;
import java.util.Optional;

/** 文本违规记录仓储（content_text_violation）。 */
public interface TextViolationRepository {

    void save(TextViolation v);

    Optional<TextViolation> findById(Long id);

    /**
     * 分页（按时间倒序）。
     *
     * @param confirmed null 不过滤；true 已确认；false 待复核
     * @param purpose   null 不过滤
     * @param accountId null 不过滤
     */
    List<TextViolation> page(Boolean confirmed, String purpose, Long accountId, int offset, int limit);

    long count(Boolean confirmed, String purpose, Long accountId);
}
