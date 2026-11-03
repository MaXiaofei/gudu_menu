package com.gudu.xsd.modules.backoffice.domain.repository;

import com.gudu.xsd.modules.backoffice.domain.model.OperationLog;
import com.gudu.xsd.modules.backoffice.domain.model.OperationLogFilter;

import java.util.List;

/** 运营操作日志仓储（admin_operation_log，只追加）。 */
public interface OperationLogRepository {

    void save(OperationLog log);

    /** 按时间倒序；filter 中为 null 的项不过滤。 */
    List<OperationLog> page(OperationLogFilter filter, int offset, int limit);

    long count(OperationLogFilter filter);
}
