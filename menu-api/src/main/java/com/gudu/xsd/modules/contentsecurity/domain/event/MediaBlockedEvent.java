package com.gudu.xsd.modules.contentsecurity.domain.event;

import com.gudu.xsd.common.domain.AbstractDomainEvent;

/**
 * 用户上传的图片被判违规（文件已隔离）。各上下文如需额外处理（清头像字段、帖子转人工复核等）可订阅本事件；
 * 不订阅也安全——文件已移出上传目录，所有引用处加载失败即不可见。
 */
public final class MediaBlockedEvent extends AbstractDomainEvent {

    private final String mediaUrl;
    private final Long memberId;
    private final String purpose;
    private final int label;

    public MediaBlockedEvent(String mediaUrl, Long memberId, String purpose, int label) {
        this.mediaUrl = mediaUrl;
        this.memberId = memberId;
        this.purpose = purpose;
        this.label = label;
    }

    public String mediaUrl() {
        return mediaUrl;
    }

    public Long memberId() {
        return memberId;
    }

    public String purpose() {
        return purpose;
    }

    public int label() {
        return label;
    }
}
