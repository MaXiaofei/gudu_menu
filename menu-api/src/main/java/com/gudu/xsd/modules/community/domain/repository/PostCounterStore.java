package com.gudu.xsd.modules.community.domain.repository;

import com.gudu.xsd.modules.community.domain.model.CounterDelta;
import com.gudu.xsd.modules.community.domain.model.PostCounter;

import java.util.Collection;
import java.util.Map;

/**
 * 帖子计数增量缓存（Redis）：互动时只累加增量，定时任务把增量回刷到 MySQL。
 * 展示值 = 数据库值 + 尚未回刷的增量。
 */
public interface PostCounterStore {

    void incr(Long postId, PostCounter counter, long delta);

    /** 尚未回刷的增量（无增量的帖子不出现在结果里）。 */
    Map<Long, CounterDelta> pending(Collection<Long> postIds);

    /** 取走最多 max 个帖子的增量（取走后不再计入 pending），交给调用方写库。 */
    Map<Long, CounterDelta> drain(int max);
}
