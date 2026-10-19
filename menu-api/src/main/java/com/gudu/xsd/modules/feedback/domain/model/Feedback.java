package com.gudu.xsd.modules.feedback.domain.model;

import com.gudu.xsd.common.domain.AggregateRoot;
import com.gudu.xsd.common.domain.DomainException;
import com.gudu.xsd.common.domain.UploadedImageUrl;

import java.time.LocalDateTime;
import java.util.List;

/** 意见反馈（聚合根）：提交时校验内容/联系方式/图片；运营侧标记处理状态。 */
public final class Feedback extends AggregateRoot<Long> {

    public static final int MIN_CONTENT = 5;
    public static final int MAX_CONTENT = 500;
    public static final int MAX_CONTACT = 50;
    public static final int MAX_IMAGES = 3;
    public static final int MAX_CLIENT_INFO = 200;

    private Long id;
    private final Long memberId;
    private final String content;
    private final String contact;
    private final List<String> images;
    private final String clientInfo;
    private FeedbackStatus status;
    private final LocalDateTime createTime;

    private Feedback(Long id, Long memberId, String content, String contact, List<String> images,
                     String clientInfo, FeedbackStatus status, LocalDateTime createTime) {
        this.id = id;
        this.memberId = memberId;
        this.content = content;
        this.contact = contact;
        this.images = images == null ? List.of() : List.copyOf(images);
        this.clientInfo = clientInfo;
        this.status = status == null ? FeedbackStatus.NEW : status;
        this.createTime = createTime;
    }

    /** 用户提交反馈。 */
    public static Feedback submit(Long memberId, String content, String contact, List<String> images,
                                  String clientInfo, LocalDateTime now) {
        if (memberId == null) throw new DomainException("请先登录");
        String c = content == null ? "" : content.trim();
        if (c.length() < MIN_CONTENT || c.length() > MAX_CONTENT) throw new DomainException("反馈内容需 5–500 字");
        String ct = blankToNull(contact);
        if (ct != null && ct.length() > MAX_CONTACT) throw new DomainException("联系方式最多 50 字");
        List<String> imgs = images == null ? List.of() : images.stream().filter(s -> s != null && !s.isBlank())
                .map(String::trim).toList();
        if (imgs.size() > MAX_IMAGES) throw new DomainException("最多上传 3 张图片");
        imgs.forEach(UploadedImageUrl::new);
        String ci = blankToNull(clientInfo);
        if (ci != null && ci.length() > MAX_CLIENT_INFO) ci = ci.substring(0, MAX_CLIENT_INFO);
        return new Feedback(null, memberId, c, ct, imgs, ci, FeedbackStatus.NEW, now);
    }

    public static Feedback rehydrate(Long id, Long memberId, String content, String contact, List<String> images,
                                     String clientInfo, FeedbackStatus status, LocalDateTime createTime) {
        return new Feedback(id, memberId, content, contact, images, clientInfo, status, createTime);
    }

    /** 运营标记处理状态（NEW ↔ DONE）。 */
    public void changeStatus(FeedbackStatus newStatus) {
        if (newStatus == null) throw new DomainException("反馈状态不合法");
        this.status = newStatus;
    }

    public void identify(Long newId) {
        if (this.id != null && !this.id.equals(newId)) throw new DomainException("聚合 id 不可变更");
        this.id = newId;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    @Override
    public Long id() {
        return id;
    }

    public Long memberId() {
        return memberId;
    }

    public String content() {
        return content;
    }

    public String contact() {
        return contact;
    }

    public List<String> images() {
        return images;
    }

    public String clientInfo() {
        return clientInfo;
    }

    public FeedbackStatus status() {
        return status;
    }

    public LocalDateTime createTime() {
        return createTime;
    }
}
