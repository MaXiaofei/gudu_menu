package com.gudu.xsd.modules.aiguard.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gudu.xsd.modules.ai.AiCallLog;
import com.gudu.xsd.modules.ai.mapper.AiCallLogMapper;
import com.gudu.xsd.modules.aiguard.domain.port.AiSpendReader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 今日花费 = ai_call_log 中 create_time ≥ from 的 cost_fen 之和（走 idx_aicall_capability 之外的时间范围扫描，当日数据量小）。 */
@Repository
@RequiredArgsConstructor
public class MybatisAiSpendReader implements AiSpendReader {

    private final AiCallLogMapper mapper;

    @Override
    public BigDecimal spentFenSince(LocalDateTime from) {
        List<Map<String, Object>> rows = mapper.selectMaps(new QueryWrapper<AiCallLog>()
                .select("IFNULL(SUM(cost_fen), 0) AS spent").ge("create_time", from));
        if (rows.isEmpty() || rows.get(0).get("spent") == null) return BigDecimal.ZERO;
        return new BigDecimal(rows.get(0).get("spent").toString());
    }
}
