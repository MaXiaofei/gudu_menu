package com.gudu.xsd.modules.privacy.application;

import com.gudu.xsd.common.privacy.RetentionPurger;
import com.gudu.xsd.common.tenant.TenantContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据保存期限定时清理（mini-release-compliance C.1，隐私政策 §三.2、§六.2）。
 *
 * <ul>
 *   <li>每天 03:30（Asia/Shanghai）执行；{@code gudu.retention.enabled=false} 关闭；</li>
 *   <li>策略天数默认取各 {@link RetentionPurger#defaultDays()}（AI 对话 180、AI 调用日志 180、反馈 365），
 *       {@code gudu.retention.days.<policy>} 可覆盖，≤0 表示该策略不清理；</li>
 *   <li>分批删除：每批 {@code gudu.retention.batch-size}（默认 500）行、每批独立提交（本类不开事务），
 *       单策略单次最多 {@code gudu.retention.max-batches} 批，剩余留到次日，避免长事务与长时间占用；</li>
 *   <li>租户：被清理的都是全局表（不按家庭过滤），显式以系统模式运行，避免后台线程无 TenantContext 被租户插件拒绝；
 *       单个策略失败只记日志，不影响其他策略。</li>
 * </ul>
 */
@Slf4j
@Component
public class DataRetentionJob {

    private final List<RetentionPurger> purgers;
    private final Environment env;
    private final Clock clock;
    private final boolean enabled;
    private final int batchSize;
    private final int maxBatches;

    public DataRetentionJob(List<RetentionPurger> purgers, Environment env, Clock clock,
                            @Value("${gudu.retention.enabled:true}") boolean enabled,
                            @Value("${gudu.retention.batch-size:500}") int batchSize,
                            @Value("${gudu.retention.max-batches:2000}") int maxBatches) {
        this.purgers = purgers;
        this.env = env;
        this.clock = clock;
        this.enabled = enabled;
        this.batchSize = Math.max(1, batchSize);
        this.maxBatches = Math.max(1, maxBatches);
    }

    @Scheduled(cron = "${gudu.retention.cron:0 30 3 * * *}", zone = "Asia/Shanghai")
    public void scheduled() {
        if (!enabled) return;
        Map<String, Integer> r = runOnce();
        log.info("数据保存期限清理完成：{}", r);
    }

    /** 执行一轮清理，返回「策略/实现类」→ 删除行数（-1 = 失败）。 */
    public Map<String, Integer> runOnce() {
        LocalDateTime now = LocalDateTime.now(clock);
        Map<String, Integer> result = new LinkedHashMap<>();
        for (RetentionPurger p : purgers) {
            int days = env.getProperty("gudu.retention.days." + p.policy(), Integer.class, p.defaultDays());
            String name = p.policy() + "/" + p.getClass().getSimpleName();
            if (days <= 0) {
                result.put(name, 0);
                continue;
            }
            LocalDateTime cutoff = now.minusDays(days);
            try {
                result.put(name, TenantContextHolder.callAsSystem(() -> purge(p, cutoff)));
            } catch (RuntimeException e) {
                log.error("保存期限清理失败 [{}]：{}", name, e.getMessage(), e);
                result.put(name, -1);
            }
        }
        return result;
    }

    private int purge(RetentionPurger p, LocalDateTime cutoff) {
        int total = 0;
        for (int i = 0; i < maxBatches; i++) {
            int n = p.purgeBatch(cutoff, batchSize);
            if (n <= 0) break;
            total += n;
        }
        return total;
    }
}
