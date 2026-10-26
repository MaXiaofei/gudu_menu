package com.gudu.xsd.modules.privacy.domain.repository;

import com.gudu.xsd.modules.privacy.domain.model.ConsentDocType;
import com.gudu.xsd.modules.privacy.domain.model.ConsentRecord;

import java.util.List;
import java.util.Optional;

/** 同意记录仓储（consent_record，全局表，按账号归属；只追加）。 */
public interface ConsentRepository {

    void append(ConsentRecord record);

    /** 该账号该类型最新一条记录。 */
    Optional<ConsentRecord> latest(Long accountId, ConsentDocType type);

    /** 该账号全部记录（按时间正序，导出用）。 */
    List<ConsentRecord> history(Long accountId);

    /** 账号合并：记录改指目标账号，返回改动条数。 */
    int reassignAccount(Long from, Long to);
}
