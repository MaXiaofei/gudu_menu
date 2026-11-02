package com.gudu.xsd.modules.aiguard.infrastructure;

import com.gudu.xsd.modules.ai.AiCallLogService;
import com.gudu.xsd.modules.aiguard.domain.port.AiSpendReader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 今日花费 = ai_call_log 中 create_time ≥ from 的 cost_fen 之和；跨模块经 ai 模块的 {@link AiCallLogService} 读取（架构守护：不直接用对方 Mapper）。 */
@Repository
@RequiredArgsConstructor
public class MybatisAiSpendReader implements AiSpendReader {

    private final AiCallLogService callLogs;

    @Override
    public BigDecimal spentFenSince(LocalDateTime from) {
        return callLogs.spentFenSince(from);
    }
}
