package com.gudu.xsd.modules.aiguard;

import com.gudu.xsd.common.security.AiQuotaCategory;
import com.gudu.xsd.modules.aiguard.domain.port.AiSpendReader;
import com.gudu.xsd.modules.aiguard.domain.port.AiUsageCounter;
import com.gudu.xsd.modules.aiguard.domain.repository.AiGuardConfigRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** AI 额度测试用的内存替身。 */
final class AiGuardFakes {

    private AiGuardFakes() {
    }

    /** 可调时间的时钟（北京时间）。 */
    static final class MutableClock extends Clock {
        Instant now;

        MutableClock(String iso) {
            now = Instant.parse(iso);
        }

        void plusSeconds(long s) {
            now = now.plusSeconds(s);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("Asia/Shanghai");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    static final class Counter implements AiUsageCounter {
        final Map<String, Long> users = new HashMap<>();
        final Map<String, Long> totals = new HashMap<>();
        boolean down;

        @Override
        public long incrementUser(AiQuotaCategory c, Long accountId, LocalDate day) {
            if (down) throw new UsageStoreException("redis down", null);
            return users.merge(c + ":" + accountId + ":" + day, 1L, Long::sum);
        }

        @Override
        public void incrementTotal(AiQuotaCategory c, LocalDate day) {
            if (down) throw new UsageStoreException("redis down", null);
            totals.merge(c + ":" + day, 1L, Long::sum);
        }

        @Override
        public Map<AiQuotaCategory, Long> totals(LocalDate day) {
            if (down) throw new UsageStoreException("redis down", null);
            Map<AiQuotaCategory, Long> m = new EnumMap<>(AiQuotaCategory.class);
            for (AiQuotaCategory c : AiQuotaCategory.values()) m.put(c, totals.getOrDefault(c + ":" + day, 0L));
            return m;
        }
    }

    static final class ConfigRepo implements AiGuardConfigRepository {
        final Map<String, String> stored = new HashMap<>();
        final List<ChangeLog> logs = new ArrayList<>();
        int loads;
        boolean failing;

        @Override
        public Map<String, String> loadAll() {
            loads++;
            if (failing) throw new RuntimeException("db down");
            return new HashMap<>(stored);
        }

        @Override
        public void saveAll(List<Change> changes, Long operatorId, String operatorName, LocalDateTime at) {
            for (Change c : changes) {
                stored.put(c.key(), c.newValue());
                logs.add(0, new ChangeLog(c.key(), c.oldValue(), c.newValue(), operatorId, operatorName, at));
            }
        }

        @Override
        public List<ChangeLog> recentLogs(int limit) {
            return logs.stream().limit(limit).toList();
        }
    }

    /** 按「今天零点」起算返回花费；记录被问到的 from。 */
    static final class Spend implements AiSpendReader {
        BigDecimal fen = BigDecimal.ZERO;
        boolean failing;
        int reads;
        LocalDateTime lastFrom;

        @Override
        public BigDecimal spentFenSince(LocalDateTime from) {
            reads++;
            lastFrom = from;
            if (failing) throw new RuntimeException("db down");
            return fen;
        }
    }
}
