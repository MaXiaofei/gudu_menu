package com.gudu.xsd.modules.aiguard.application;

import com.gudu.xsd.common.BizException;
import com.gudu.xsd.common.security.AiQuotaCategory;
import com.gudu.xsd.common.security.AiQuotaGate;
import com.gudu.xsd.modules.aiguard.domain.port.AiUsageCounter;
import com.gudu.xsd.modules.aiguard.domain.port.AiUsageCounter.UsageStoreException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 每登录账号每日 AI 次数配额：拍照识别 / 一周餐程 / 咕嘟对话 / 其他 AI 各算各的，上限来自
 * {@link AiGuardConfigService}。日期按 Asia/Shanghai，次日自动恢复（Redis 键带日期，48h 过期）。
 *
 * <p>Redis 故障 fail-closed：计数不可用时拒绝（{@link #require} 抛 503，{@link #tryConsume} 返回 false）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiQuotaService implements AiQuotaGate {

    /** 额度日界线用北京时间，与服务器时区无关。 */
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    public static final String UNAVAILABLE_MESSAGE = "AI 服务暂时不可用，请稍后再试";

    enum Outcome { OK, EXCEEDED, UNAVAILABLE }

    private final AiGuardConfigService config;
    private final AiUsageCounter counter;
    private final Clock clock;

    @Override
    public void require(AiQuotaCategory category, Long accountId) {
        switch (consume(category, accountId)) {
            case EXCEEDED -> throw BizException.tooManyRequests(message(category));
            case UNAVAILABLE -> throw new BizException(503, UNAVAILABLE_MESSAGE);
            default -> { }
        }
    }

    @Override
    public boolean tryConsume(AiQuotaCategory category, Long accountId) {
        return consume(category, accountId) == Outcome.OK;
    }

    Outcome consume(AiQuotaCategory category, Long accountId) {
        if (accountId == null) return Outcome.UNAVAILABLE; // 没有账号就没法按人计数，拒绝
        int limit = config.current().limit(category);
        LocalDate day = today();
        long n;
        try {
            n = counter.incrementUser(category, accountId, day);
        } catch (UsageStoreException e) {
            log.warn("AI 次数计数失败（拒绝本次） category={} account={} err={}", category, accountId, e.getMessage());
            return Outcome.UNAVAILABLE;
        }
        if (limit > 0 && n > limit) return Outcome.EXCEEDED;
        try {
            counter.incrementTotal(category, day);
        } catch (UsageStoreException e) {
            log.debug("AI 全站次数统计失败（不影响放行）: {}", e.getMessage()); // 只是后台展示用
        }
        return Outcome.OK;
    }

    /** 今日（北京时间）。 */
    public LocalDate today() {
        return clock.instant().atZone(ZONE).toLocalDate();
    }

    private String message(AiQuotaCategory category) {
        return category == AiQuotaCategory.OTHER
                ? "今日 AI 调用已达上限（" + config.current().limit(category) + " 次），明天再试"
                : category.exhaustedMessage();
    }
}
