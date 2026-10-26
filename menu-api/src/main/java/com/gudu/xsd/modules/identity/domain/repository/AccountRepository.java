package com.gudu.xsd.modules.identity.domain.repository;

import com.gudu.xsd.modules.identity.domain.model.Account;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/** 账号仓储（account 表 + account_merge_log 审计表）。 */
public interface AccountRepository {

    Optional<Account> findById(Long id);

    /** 按登录名（手机号 / 历史字面量 admin）查，含非 ACTIVE 账号。 */
    Optional<Account> findByPhone(String phone);

    Optional<Account> findByOpenid(String openid);

    /** 新账号插入后回填 id（{@link Account#identify}）；已有账号全量更新可变字段。 */
    void save(Account account);

    /** 批量取账号。 */
    java.util.List<Account> findAllByIds(Collection<Long> ids);

    /** 批量取昵称（运营后台列表、社区作者名等跨上下文展示用）。 */
    Map<Long, String> nicknames(Collection<Long> ids);

    /** 账号合并审计：不可回滚但可追溯。 */
    void recordMerge(Long sourceId, Long targetId, Long operatorId, String detail, LocalDateTime at);
}
