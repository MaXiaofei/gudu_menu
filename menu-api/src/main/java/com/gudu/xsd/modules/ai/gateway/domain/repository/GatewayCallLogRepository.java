package com.gudu.xsd.modules.ai.gateway.domain.repository;

import com.gudu.xsd.modules.ai.gateway.domain.model.GatewayCallRecord;

import java.time.LocalDateTime;
import java.util.List;

/** 网关调用日志（落 ai_call_log，capability 非空的行）。 */
public interface GatewayCallLogRepository {

    void append(GatewayCallRecord record);

    List<GatewayCallRecord> since(LocalDateTime from);
}
