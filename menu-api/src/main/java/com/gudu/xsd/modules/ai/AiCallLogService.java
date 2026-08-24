package com.gudu.xsd.modules.ai;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gudu.xsd.common.PageQuery;
import com.gudu.xsd.modules.ai.mapper.AiCallLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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

    /**
     * 调用日志分页查询。
     * 支持按 scene（nutrition_fill/menu_recommend/dish_estimate）和 status（ok/fail）筛选。
     */
    public IPage<AiCallLog> page(PageQuery q, String scene, String status) {
        QueryWrapper<AiCallLog> w = new QueryWrapper<>();
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
        return callLogMapper.selectPage(page, w);
    }

    /**
     * Token 用量汇总：按 scene 分组统计调用次数、总 token、失败次数、平均延迟。
     *
     * @param days 限定统计天数（默认 7 天）
     */
    public List<Map<String, Object>> usage(int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        QueryWrapper<AiCallLog> w = new QueryWrapper<>();
        w.ge("create_time", since);
        List<AiCallLog> logs = callLogMapper.selectList(w);

        // 按 scene 分组聚合
        Map<String, Map<String, Object>> byScene = new LinkedHashMap<>();
        for (AiCallLog log : logs) {
            String key = log.getScene() != null ? log.getScene() : "unknown";
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
        // 计算平均延迟 + 清理临时字段
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> agg : byScene.values()) {
            int total = (int) agg.get("totalCalls");
            int latencySum = (int) agg.remove("_latencySum");
            agg.put("latencyAvgMs", total > 0 ? latencySum / total : 0);
            result.add(agg);
        }
        return result;
    }
}
