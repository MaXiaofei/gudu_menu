package com.gudu.xsd.modules.contentsecurity.domain.repository;

import com.gudu.xsd.modules.contentsecurity.domain.model.MediaCheck;

import java.util.Optional;

public interface MediaCheckRepository {

    void save(MediaCheck check);

    Optional<MediaCheck> findByTraceId(String traceId);

    /** 该图片地址最近一次检测。 */
    Optional<MediaCheck> latestByUrl(String mediaUrl);
}
