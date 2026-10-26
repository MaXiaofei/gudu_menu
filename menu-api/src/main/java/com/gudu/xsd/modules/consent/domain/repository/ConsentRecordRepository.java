package com.gudu.xsd.modules.consent.domain.repository;

import com.gudu.xsd.modules.consent.domain.model.ConsentRecord;

import java.util.List;

/** 同意记录仓储（consent_record，全局表，只追加）。 */
public interface ConsentRecordRepository {

    void save(ConsentRecord record);

    /** 某账号的全部记录，按时间倒序（最多 limit 条）。 */
    List<ConsentRecord> listByAccount(Long accountId, int limit);

    /** 账号合并（AccountMerged）：把归属 from 的记录改指 to。返回改动条数。 */
    int reassignAccount(Long from, Long to);
}
