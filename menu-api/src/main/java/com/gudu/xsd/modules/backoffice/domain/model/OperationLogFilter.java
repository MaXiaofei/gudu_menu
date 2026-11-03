package com.gudu.xsd.modules.backoffice.domain.model;

import java.time.LocalDateTime;

/**
 * 操作日志查询条件。各项为 null 表示不过滤；时间区间为 [from, to)。
 *
 * @param targetType 对象类型
 * @param targetId   对象 id
 * @param operatorId 操作人账号 id
 * @param from       起始时间（含）
 * @param to         结束时间（不含）
 */
public record OperationLogFilter(OperationTarget targetType, Long targetId, Long operatorId,
                                 LocalDateTime from, LocalDateTime to) {
}
