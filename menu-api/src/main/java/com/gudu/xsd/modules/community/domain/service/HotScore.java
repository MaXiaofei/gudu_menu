package com.gudu.xsd.modules.community.domain.service;

import com.gudu.xsd.modules.community.domain.model.PostCounts;

/**
 * 推荐流热度分：{@code log(赞 + 2·收藏 + 3·照着做 + 2) / (小时数 + 2)^1.5 × (1 + 话题加权)}。
 * <ul>
 *   <li>「照着做」权重最高——代表内容真的被用起来了；</li>
 *   <li>+2 保证零互动的新帖也有正分，按新旧自然排序；</li>
 *   <li>指数 1.5 的时间衰减：一天后约降到 1/30，避免老爆款长期霸榜。</li>
 * </ul>
 */
public final class HotScore {

    static final double GRAVITY = 1.5;

    private HotScore() {
    }

    public static double of(PostCounts counts, double hoursSincePublish, double topicBoost) {
        PostCounts c = counts == null ? PostCounts.ZERO : counts;
        double engagement = c.likes() + 2.0 * c.collects() + 3.0 * c.copies() + 2.0;
        double hours = Math.max(0, hoursSincePublish);
        double boost = 1.0 + Math.max(0, topicBoost);
        return Math.log(engagement) / Math.pow(hours + 2.0, GRAVITY) * boost;
    }
}
