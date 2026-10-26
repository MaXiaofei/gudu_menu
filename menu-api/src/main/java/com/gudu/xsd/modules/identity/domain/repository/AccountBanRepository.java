package com.gudu.xsd.modules.identity.domain.repository;

import com.gudu.xsd.modules.identity.domain.model.AccountBan;

import java.util.Optional;

/** 账号封禁仓储（account_ban：一行 = 当前封禁中）。 */
public interface AccountBanRepository {

    Optional<AccountBan> find(Long accountId);

    boolean exists(Long accountId);

    /** 新增或覆盖（重复封禁更新原因）。 */
    void save(AccountBan ban);

    /** 解封：删除封禁行。返回是否删除了记录。 */
    boolean delete(Long accountId);
}
