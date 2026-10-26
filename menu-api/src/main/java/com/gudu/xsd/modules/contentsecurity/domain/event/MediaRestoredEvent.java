package com.gudu.xsd.modules.contentsecurity.domain.event;

import com.gudu.xsd.common.domain.AbstractDomainEvent;

import java.util.List;

/**
 * 违规图片经人工复核为误判、已恢复（F.3）。订阅方（同事务同步执行）：
 * <ul>
 *   <li>member：上传账号因该图被清空的头像恢复为该图；</li>
 *   <li>community：因该图被隐藏的帖子恢复公开。</li>
 * </ul>
 * 隔离文件由应用层在订阅方都成功后还原。
 */
public final class MediaRestoredEvent extends AbstractDomainEvent {

    private final String mediaUrl;
    private final Long accountId;
    private final String purpose;
    /** 订阅方回写的复核提示（如「帖子仍有其他处置」），随复核结果返回给运营。 */
    private final List<String> notices = new java.util.ArrayList<>();

    public MediaRestoredEvent(String mediaUrl, Long accountId, String purpose) {
        this.mediaUrl = mediaUrl;
        this.accountId = accountId;
        this.purpose = purpose;
    }

    public String mediaUrl() {
        return mediaUrl;
    }

    /** 引用处可能存的地址：原图 + 同名缩略图。 */
    public List<String> urlVariants() {
        if (mediaUrl == null) return List.of();
        String thumb = mediaUrl.replace("/uploads/original/", "/uploads/thumbnail/");
        return thumb.equals(mediaUrl) ? List.of(mediaUrl) : List.of(mediaUrl, thumb);
    }

    /** 上传账号（content_media_check.member_id，multi-tenant-family 起存的是账号 id）。 */
    public Long accountId() {
        return accountId;
    }

    public void addNotice(String notice) {
        if (notice != null && !notices.contains(notice)) notices.add(notice);
    }

    public List<String> notices() {
        return List.copyOf(notices);
    }

    public String purpose() {
        return purpose;
    }
}
