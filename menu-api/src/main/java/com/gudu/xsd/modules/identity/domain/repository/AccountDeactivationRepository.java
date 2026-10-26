package com.gudu.xsd.modules.identity.domain.repository;

import com.gudu.xsd.modules.identity.domain.model.AccountDeactivation;
import com.gudu.xsd.modules.identity.domain.model.DeactivationStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** 注销处理记录仓储（account_deactivation）。 */
public interface AccountDeactivationRepository {

    void save(AccountDeactivation record);

    Optional<AccountDeactivation> findById(Long id);

    /** 该账号最近一次注销记录。 */
    Optional<AccountDeactivation> latestByAccount(Long accountId);

    /**
     * 分页（按受理时间倒序）。
     *
     * @param status       为 null 不过滤
     * @param overdueBefore 非 null 时只要「未完成且受理时间早于该时刻」的（粗筛超期，精确判断在领域对象）
     */
    List<AccountDeactivation> page(DeactivationStatus status, LocalDateTime overdueBefore, int offset, int limit);

    long count(DeactivationStatus status, LocalDateTime overdueBefore);
}
