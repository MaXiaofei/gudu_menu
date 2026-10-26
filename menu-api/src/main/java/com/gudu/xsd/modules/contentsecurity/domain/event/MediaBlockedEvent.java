package com.gudu.xsd.modules.contentsecurity.domain.event;

import com.gudu.xsd.common.domain.AbstractDomainEvent;

import java.util.List;

/**
 * 用户上传的图片被判违规。订阅方（同事务同步执行）：
 * <ul>
 *   <li>member：清空引用该图的头像（前端回退默认头像）；</li>
 *   <li>community：含该图的帖子转为隐藏并回到复核队列，作者可见「图片未通过审核」。</li>
 * </ul>
 * 其余用途（菜谱、食记、评价…）不订阅：发布后文件被隔离、原地址换成占位图。
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

    /** 引用处可能存的地址：原图 + 同名缩略图（上传接口两者都返回）。 */
    public List<String> urlVariants() {
        if (mediaUrl == null) return List.of();
        String thumb = mediaUrl.replace("/uploads/original/", "/uploads/thumbnail/");
        return thumb.equals(mediaUrl) ? List.of(mediaUrl) : List.of(mediaUrl, thumb);
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
