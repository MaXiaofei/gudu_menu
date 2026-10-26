package com.gudu.xsd.modules.backoffice.domain.repository;

import com.gudu.xsd.modules.backoffice.domain.model.OperationLog;
import com.gudu.xsd.modules.backoffice.domain.model.OperationTarget;

import java.util.List;

/** 运营操作日志仓储（admin_operation_log，只追加）。 */
public interface OperationLogRepository {

    void save(OperationLog log);

    /** 按时间倒序；targetType / targetId 为 null 不过滤。 */
    List<OperationLog> page(OperationTarget targetType, Long targetId, int offset, int limit);

    long count(OperationTarget targetType, Long targetId);
}
