package com.gudu.xsd.modules.ai.gateway.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gudu.xsd.modules.ai.AiCallLog;
import com.gudu.xsd.modules.ai.gateway.domain.model.Capability;
import com.gudu.xsd.modules.ai.gateway.domain.model.GatewayCallRecord;
import com.gudu.xsd.modules.ai.gateway.domain.model.TokenUsage;
import com.gudu.xsd.modules.ai.gateway.domain.repository.GatewayCallLogRepository;
import com.gudu.xsd.modules.ai.mapper.AiCallLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/** 网关尝试行写入 ai_call_log（capability 非空），用量统计只读这些行。 */
@Repository
@RequiredArgsConstructor
public class AiCallLogGatewayRepository implements GatewayCallLogRepository {

    /** 用量统计最多读取的行数（防止 7 天窗口过大拖垮查询）。 */
    static final int MAX_ROWS = 20_000;

    private final AiCallLogMapper mapper;

    @Override
    public void append(GatewayCallRecord r) {
        AiCallLog row = new AiCallLog();
        row.setScene(truncate(r.scene(), 32));
        row.setMemberId(r.memberId());
        row.setCapability(r.capability().name());
        row.setProvider(truncate(r.provider(), 64));
        row.setModel(truncate(r.model(), 64));
        row.setTokensIn(r.usage().promptTokens());
        row.setTokensOut(r.usage().completionTokens());
        row.setCostFen(r.costFen());
        row.setCost(r.costFen() == null ? BigDecimal.ZERO : r.costFen().divide(BigDecimal.valueOf(100), 4,
                RoundingMode.HALF_UP));
        row.setLatencyMs((int) Math.min(Integer.MAX_VALUE, r.ms()));
        row.setStatus(r.ok() ? "ok" : "fail");
        row.setErrorMsg(truncate(r.error(), 500));
        row.setTraceId(truncate(r.traceId(), 64));
        row.setCreateTime(r.at());
        mapper.insert(row);
    }

    @Override
    public List<GatewayCallRecord> since(LocalDateTime from) {
        return mapper.selectList(new QueryWrapper<AiCallLog>().isNotNull("capability").ge("create_time", from)
                        .orderByDesc("id").last("LIMIT " + MAX_ROWS))
                .stream().map(r -> new GatewayCallRecord(Capability.of(r.getCapability()), r.getScene(),
                        r.getProvider(), r.getModel(), "ok".equals(r.getStatus()), r.getErrorMsg(),
                        r.getLatencyMs() == null ? 0 : r.getLatencyMs(),
                        new TokenUsage(nz(r.getTokensIn()), 0, nz(r.getTokensOut())), r.getCostFen(), r.getTraceId(),
                        r.getMemberId(), r.getCreateTime()))
                .toList();
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
