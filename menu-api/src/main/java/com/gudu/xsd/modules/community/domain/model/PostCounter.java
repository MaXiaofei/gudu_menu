package com.gudu.xsd.modules.community.domain.model;

/** 帖子计数字段（Redis 增量 → 定时回刷 MySQL）。 */
public enum PostCounter {
    LIKE, COLLECT, COMMENT, COPY
}
