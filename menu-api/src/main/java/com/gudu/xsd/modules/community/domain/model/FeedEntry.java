package com.gudu.xsd.modules.community.domain.model;

/** Feed 候选条目：帖子 id + 排序分（热度分或发布时间毫秒）+ 发布时间毫秒（用于 asOf 截断）。 */
public record FeedEntry(long postId, double score, long createdEpochMs) {
}
