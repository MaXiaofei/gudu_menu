package com.gudu.xsd.modules.ai;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gudu.xsd.common.privacy.RetentionPurger;
import com.gudu.xsd.modules.ai.mapper.AiCallLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** AI 调用日志 ai_call_log 保存 180 天（含网关单次尝试行）。依赖 V89 索引 idx_aicall_time(create_time)。 */
@Component
@RequiredArgsConstructor
public class AiCallLogRetentionPurger implements RetentionPurger {

    private final AiCallLogMapper mapper;

    @Override
    public String policy() {
        return "ai-call-log";
    }

    @Override
    public int defaultDays() {
        return 180;
    }

    @Override
    public int purgeBatch(LocalDateTime cutoff, int limit) {
        return mapper.delete(new QueryWrapper<AiCallLog>().lt("create_time", cutoff).last("LIMIT " + Math.max(1, limit)));
    }
}
