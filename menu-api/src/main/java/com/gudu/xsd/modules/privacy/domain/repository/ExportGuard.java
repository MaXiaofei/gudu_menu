package com.gudu.xsd.modules.privacy.domain.repository;

import java.time.Duration;
import java.util.Optional;

/** 个人数据导出的限频与临时下载凭证（Redis 实现）。 */
public interface ExportGuard {

    /** 当日导出次数 +1 并返回累计值（自然日，Asia/Shanghai）。 */
    long incrementToday(Long accountId);

    /** 签发临时下载凭证（随机 token → accountId），ttl 后自动失效。 */
    String issueToken(Long accountId, Duration ttl);

    /** 解析凭证（有效期内可重复下载，便于浏览器重试 / 链接预览后再打开）。 */
    Optional<Long> resolveToken(String token);
}
