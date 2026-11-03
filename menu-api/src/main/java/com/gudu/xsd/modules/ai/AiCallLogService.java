package com.gudu.xsd.modules.ai;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gudu.xsd.common.PageQuery;
import com.gudu.xsd.modules.ai.mapper.AiCallLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 调用日志查询与 Token 用量聚合（管理后台用）。
 * 从 AiController 下沉（架构守护：Controller 不得直接依赖 Mapper）。
 */
@Service
@RequiredArgsConstructor
public class AiCallLogService {

    private final AiCallLogMapper callLogMapper;

    /** 自 from 起的模型花费合计（分）：AI 额度的全站每日花费上限用（只有网关尝试行带 cost_fen，业务行为空，不会重复计；与明细页成本口径一致：cost_fen 优先，业务行 cost 恒为 0）。 */
    public java.math.BigDecimal spentFenSince(LocalDateTime from) {
        List<Map<String, Object>> rows = callLogMapper.selectMaps(new QueryWrapper<AiCallLog>()
                .select("IFNULL(SUM(cost_fen), 0) AS spent").ge("create_time", from));
        if (rows.isEmpty() || rows.get(0) == null || rows.get(0).get("spent") == null) return java.math.BigDecimal.ZERO;
        return new java.math.BigDecimal(rows.get(0).get("spent").toString());
    }

    /** 业务行与网关行判重的时间窗（秒）：网关行写于调用过程中，业务行写于调用结束后。 */
    static final int DEDUP_WINDOW_SECONDS = 180;
    /** 不调模型的业务行 provider（向量检索推荐），永远保留。 */
    static final String NO_MODEL_PROVIDER = "vector";

    /**
     * 「应展示/计入」的行：全部网关尝试行（capability 非空，含降级的失败尝试）+ 业务行里
     * 不调模型的行或没有对应网关行的行（业务行与同场景同成员、时间窗内的网关行视为同一次调用，只计网关行）。
     */
    static final String COUNTED_SQL = "(capability IS NOT NULL OR provider = '" + NO_MODEL_PROVIDER + "'"
            + " OR NOT EXISTS (SELECT 1 FROM ai_call_log g WHERE g.capability IS NOT NULL AND g.scene = ai_call_log.scene"
            + " AND g.member_id <=> ai_call_log.member_id"
            + " AND g.create_time >= DATE_SUB(ai_call_log.create_time, INTERVAL " + DEDUP_WINDOW_SECONDS + " SECOND)"
            + " AND g.create_time <= ai_call_log.create_time))";

    /** 成本（元）：优先 cost_fen/100，缺失再用 cost。 */
    public static BigDecimal costYuan(AiCallLog r) {
        if (r.getCostFen() != null) return r.getCostFen().divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        return r.getCost() == null ? BigDecimal.ZERO : r.getCost();
    }

    /** 内存版判重（与 COUNTED_SQL 同口径）：返回应计入的行。 */
    static List<AiCallLog> counted(List<AiCallLog> logs) {
        List<AiCallLog> gateway = logs.stream().filter(l -> l.getCapability() != null).toList();
        List<AiCallLog> out = new ArrayList<>();
        for (AiCallLog l : logs) {
            if (l.getCapability() != null || NO_MODEL_PROVIDER.equals(l.getProvider())) {
                out.add(l);
                continue;
            }
            boolean dup = l.getCreateTime() != null && gateway.stream().anyMatch(g ->
                    java.util.Objects.equals(g.getScene(), l.getScene())
                            && java.util.Objects.equals(g.getMemberId(), l.getMemberId())
                            && g.getCreateTime() != null
                            && !g.getCreateTime().isBefore(l.getCreateTime().minusSeconds(DEDUP_WINDOW_SECONDS))
                            && !g.getCreateTime().isAfter(l.getCreateTime()));
            if (!dup) out.add(l);
        }
        return out;
    }

    /**
     * 调用日志分页查询（网关尝试行 + 无对应网关行的业务行）。
     * 支持按 scene 和 status（ok/fail）筛选。
     */
    public IPage<AiCallLog> page(PageQuery q, String scene, String status) {
        QueryWrapper<AiCallLog> w = new QueryWrapper<AiCallLog>().apply(COUNTED_SQL);
        if (scene != null && !scene.isBlank()) {
            w.eq("scene", scene);
        }
        if (status != null && !status.isBlank()) {
            w.eq("status", status);
        }
        w.orderByDesc("create_time");
        IPage<AiCallLog> page = new Page<>(
                q.getPageNum() == null ? 1 : q.getPageNum(),
                q.getPageSize() == null ? 15 : q.getPageSize());
        IPage<AiCallLog> res = callLogMapper.selectPage(page, w);
        if (res != null && res.getRecords() != null) {
            res.getRecords().forEach(r -> r.setCostYuan(costYuan(r)));
        }
        return res;
    }

    /**
     * Token 用量汇总：按 scene 分组统计调用次数、总 token、失败次数、平均延迟、成本（元）。
     *
     * @param days 限定统计天数（默认 7 天）
     */
    public List<Map<String, Object>> usage(int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        // 业务行判重需要回看窗口前的网关行，故多读一个时间窗再判重，最后只保留窗口内的行
        QueryWrapper<AiCallLog> wide = new QueryWrapper<AiCallLog>()
                .ge("create_time", since.minusSeconds(DEDUP_WINDOW_SECONDS));
        List<AiCallLog> logs = counted(callLogMapper.selectList(wide)).stream()
                .filter(l -> l.getCreateTime() == null || !l.getCreateTime().isBefore(since)).toList();
        return aggregate(logs);
    }

    static List<Map<String, Object>> aggregate(List<AiCallLog> logs) {
        Map<String, Map<String, Object>> byScene = new LinkedHashMap<>();
        Map<String, BigDecimal> cost = new LinkedHashMap<>();
        for (AiCallLog log : logs) {
            String key = log.getScene() != null ? log.getScene() : "unknown";
            cost.merge(key, costYuan(log), BigDecimal::add);
            Map<String, Object> agg = byScene.computeIfAbsent(key, k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("scene", k);
                m.put("totalCalls", 0);
                m.put("failCalls", 0);
                m.put("tokensIn", 0);
                m.put("tokensOut", 0);
                m.put("latencyAvgMs", 0);
                m.put("_latencySum", 0);
                return m;
            });
            agg.put("totalCalls", (int) agg.get("totalCalls") + 1);
            if ("fail".equals(log.getStatus())) {
                agg.put("failCalls", (int) agg.get("failCalls") + 1);
            }
            agg.put("tokensIn", (int) agg.get("tokensIn") + (log.getTokensIn() != null ? log.getTokensIn() : 0));
            agg.put("tokensOut", (int) agg.get("tokensOut") + (log.getTokensOut() != null ? log.getTokensOut() : 0));
            if (log.getLatencyMs() != null) {
                agg.put("_latencySum", (int) agg.get("_latencySum") + log.getLatencyMs());
            }
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> agg : byScene.values()) {
            int total = (int) agg.get("totalCalls");
            int latencySum = (int) agg.remove("_latencySum");
            agg.put("latencyAvgMs", total > 0 ? latencySum / total : 0);
            agg.put("costYuan", cost.get((String) agg.get("scene")));
            result.add(agg);
        }
        return result;
    }
}
