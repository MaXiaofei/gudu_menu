package com.gudu.xsd.modules.aiguard.domain.port;

import com.gudu.xsd.common.security.AiQuotaCategory;

import java.time.LocalDate;
import java.util.Map;

/** AI 次数计数器（Redis）。存储不可用时抛 {@link UsageStoreException}，由调用方按 fail-closed 处理。 */
public interface AiUsageCounter {

    /** 某账号某类今日已用次数加一，返回加一后的值。 */
    long incrementUser(AiQuotaCategory category, Long accountId, LocalDate day);

    /** 某类今日全站被放行的次数加一（后台展示用）。 */
    void incrementTotal(AiQuotaCategory category, LocalDate day);

    /** 各类今日全站被放行次数。 */
    Map<AiQuotaCategory, Long> totals(LocalDate day);

    /** 计数存储故障。 */
    class UsageStoreException extends RuntimeException {
        public UsageStoreException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
