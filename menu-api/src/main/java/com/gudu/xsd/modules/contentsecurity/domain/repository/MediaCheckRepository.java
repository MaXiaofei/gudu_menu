package com.gudu.xsd.modules.contentsecurity.domain.repository;

import com.gudu.xsd.modules.contentsecurity.domain.model.MediaCheck;

import java.util.List;
import java.util.Optional;

public interface MediaCheckRepository {

    void save(MediaCheck check);

    Optional<MediaCheck> findByTraceId(String traceId);

    /** 该图片地址最近一次检测。 */
    Optional<MediaCheck> latestByUrl(String mediaUrl);

    Optional<MediaCheck> findById(Long id);

    /**
     * 违规图片（status=RISKY）分页，按时间倒序（F.3 复核列表）。
     *
     * @param review    PENDING = 未复核；CONFIRMED / RESTORED = 对应复核结论；null 不过滤
     * @param purpose   null 不过滤
     * @param accountId null 不过滤
     */
    List<MediaCheck> pageViolations(String review, String purpose, Long accountId, int offset, int limit);

    /** 注销：该账号上传过的指定用途图片地址（去重）。 */
    List<String> urlsByAccount(Long accountId, com.gudu.xsd.modules.contentsecurity.domain.model.ContentPurpose purpose);

    long countViolations(String review, String purpose, Long accountId);
}
