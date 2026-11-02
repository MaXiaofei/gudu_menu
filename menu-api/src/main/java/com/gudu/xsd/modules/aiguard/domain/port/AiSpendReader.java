package com.gudu.xsd.modules.aiguard.domain.port;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 读取模型花费（ai_call_log.cost_fen 之和，分）。 */
public interface AiSpendReader {

    BigDecimal spentFenSince(LocalDateTime from);
}
