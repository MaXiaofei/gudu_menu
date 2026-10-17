package com.gudu.xsd.modules.agent.domain.model;

import com.gudu.xsd.common.domain.AggregateRoot;
import com.gudu.xsd.common.domain.DomainException;

import java.time.LocalDateTime;

/**
 * 咕嘟会话（聚合根）：一个成员的一段对话。标题取首句，最近一条消息与封面用于会话列表。
 */
public class AgentSession extends AggregateRoot<String> {

    public static final int TITLE_MAX = 30;

    private final String id;
    private final Long memberId;
    private String title;
    private String lastMessage;
    private String coverUrl;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private AgentSession(String id, Long memberId, String title, String lastMessage, String coverUrl,
                         LocalDateTime createdAt, LocalDateTime updatedAt) {
        if (id == null || id.isBlank()) throw new DomainException("会话 id 不能为空");
        if (memberId == null) throw new DomainException("会话必须属于某个成员");
        this.id = id;
        this.memberId = memberId;
        this.title = title;
        this.lastMessage = lastMessage;
        this.coverUrl = coverUrl;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static AgentSession start(String id, Long memberId, String title, LocalDateTime now) {
        return new AgentSession(id, memberId, clip(title, TITLE_MAX), null, null, now, now);
    }

    public static AgentSession restore(String id, Long memberId, String title, String lastMessage, String coverUrl,
                                       LocalDateTime createdAt, LocalDateTime updatedAt) {
        return new AgentSession(id, memberId, title, lastMessage, coverUrl, createdAt, updatedAt);
    }

    @Override
    public String id() {
        return id;
    }

    /** 新一轮对话后更新：无标题时用用户首句作标题。 */
    public void touch(String userText, String reply, String cover, LocalDateTime now) {
        if (title == null || title.isBlank()) title = clip(userText, TITLE_MAX);
        if (reply != null && !reply.isBlank()) lastMessage = clip(reply, 120);
        if (cover != null && !cover.isBlank()) coverUrl = cover;
        updatedAt = now;
    }

    public boolean ownedBy(Long member) {
        return memberId.equals(member);
    }

    public Long memberId() {
        return memberId;
    }

    public String title() {
        return title;
    }

    public String lastMessage() {
        return lastMessage;
    }

    public String coverUrl() {
        return coverUrl;
    }

    public LocalDateTime createdAt() {
        return createdAt;
    }

    public LocalDateTime updatedAt() {
        return updatedAt;
    }

    private static String clip(String s, int max) {
        if (s == null) return null;
        String t = s.trim().replaceAll("\\s+", " ");
        return t.length() > max ? t.substring(0, max) : t;
    }
}
