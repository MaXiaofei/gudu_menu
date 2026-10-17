package com.gudu.xsd.modules.community.application;

import com.gudu.xsd.modules.community.domain.model.CounterDelta;
import com.gudu.xsd.modules.community.domain.model.PostCounter;
import com.gudu.xsd.modules.community.domain.repository.PostCounterStore;
import com.gudu.xsd.modules.community.domain.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

/** 每分钟把 Redis 里的互动计数增量回刷到 community_post（Redis 不可用时跳过，下次再刷）。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CommunityCounterFlushJob {

    static final int BATCH = 500;

    private final PostCounterStore counters;
    private final PostRepository posts;

    @Scheduled(fixedDelayString = "${gudu.community.counter-flush-ms:60000}", initialDelay = 30000)
    public void flush() {
        try {
            int total = 0;
            Map<Long, CounterDelta> batch;
            do {
                batch = counters.drain(BATCH);
                batch.forEach(this::writeBack);
                total += batch.size();
            } while (batch.size() >= BATCH);
            if (total > 0) log.debug("厨房圈计数回刷 {} 条帖子", total);
        } catch (RuntimeException e) {
            log.warn("厨房圈计数回刷失败（稍后重试）: {}", e.getMessage());
        }
    }

    /** 写库失败时把增量放回 Redis，避免已取走的增量丢失。 */
    void writeBack(Long postId, CounterDelta d) {
        try {
            posts.addCounts(postId, d);
        } catch (RuntimeException e) {
            counters.incr(postId, PostCounter.LIKE, d.likes());
            counters.incr(postId, PostCounter.COLLECT, d.collects());
            counters.incr(postId, PostCounter.COMMENT, d.comments());
            counters.incr(postId, PostCounter.COPY, d.copies());
            throw e;
        }
    }
}
