package com.gudu.xsd.common.privacy;

import java.time.LocalDateTime;

/**
 * 数据保存期限清理（mini-release-compliance C.1，隐私政策 §六.2）：各上下文实现本接口（Spring Bean），
 * 由 privacy 上下文的定时任务按天调用。
 *
 * <p>约定：{@link #purgeBatch} 每次最多物理删除 {@code limit} 行并立即提交（不开事务，避免长事务锁表）；
 * 返回本批删除行数，调度方循环调用直到返回 0。只能处理全局表（不按家庭过滤），调度方以系统模式调用。
 */
public interface RetentionPurger {

    /** 保存期限策略名（配置 {@code gudu.retention.days.<policy>} 可覆盖天数）。 */
    String policy();

    /** 默认保存天数（隐私政策承诺值）。 */
    int defaultDays();

    /** 删除早于 cutoff 的一批数据，返回删除行数（0 = 已清完）。 */
    int purgeBatch(LocalDateTime cutoff, int limit);
}
