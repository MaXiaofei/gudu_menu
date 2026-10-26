package com.gudu.xsd.modules.feedback.domain.model;

import com.gudu.xsd.common.domain.AggregateRoot;
import com.gudu.xsd.common.domain.DomainException;
import com.gudu.xsd.common.domain.UploadedImageUrl;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 意见反馈（聚合根）：提交时校验内容/联系方式/图片/类型；运营回复（NEW → REPLIED）或标记处理（→ DONE）。
 * 只有提交人本人能看到自己的反馈与回复。
 */
public final class Feedback extends AggregateRoot<Long> {

    public static final int MIN_CONTENT = 5;
    public static final int MAX_CONTENT = 500;
    public static final int MAX_CONTACT = 50;
    public static final int MAX_IMAGES = 3;
    public static final int MAX_CLIENT_INFO = 200;
    public static final int MAX_SOURCE_REF = 128;
    public static final int MAX_REPLY = 1000;

    private Long id;
    private final Long memberId;
    private final FeedbackType type;
    private final String sourceRef;
    private final String content;
    private final String contact;
    private final List<String> images;
    private final String clientInfo;
    private FeedbackStatus status;
    private String reply;
    private LocalDateTime repliedAt;
    private Long repliedBy;
    private final LocalDateTime createTime;

    private Feedback(Long id, Long memberId, FeedbackType type, String sourceRef, String content, String contact,
                     List<String> images, String clientInfo, FeedbackStatus status, String reply,
                     LocalDateTime repliedAt, Long repliedBy, LocalDateTime createTime) {
        this.id = id;
        this.memberId = memberId;
        this.type = type == null ? FeedbackType.GENERAL : type;
        this.sourceRef = sourceRef;
        this.content = content;
        this.contact = contact;
        this.images = images == null ? List.of() : List.copyOf(images);
        this.clientInfo = clientInfo;
        this.status = status == null ? FeedbackStatus.NEW : status;
        this.reply = reply;
        this.repliedAt = repliedAt;
        this.repliedBy = repliedBy;
        this.createTime = createTime;
    }

    /** 用户提交一般反馈（type=general，无来源引用）。 */
    public static Feedback submit(Long memberId, String content, String contact, List<String> images,
                                  String clientInfo, LocalDateTime now) {
        return submit(memberId, FeedbackType.GENERAL, null, content, contact, images, clientInfo, now);
    }

    /** 用户提交反馈：type 为空按 general；sourceRef（AI 消息 id / 来源页面）超长截断。 */
    public static Feedback submit(Long memberId, FeedbackType type, String sourceRef, String content, String contact,
                                  List<String> images, String clientInfo, LocalDateTime now) {
        if (memberId == null) throw new DomainException("请先登录");
        String c = content == null ? "" : content.trim();
        if (c.length() < MIN_CONTENT || c.length() > MAX_CONTENT) throw new DomainException("反馈内容需 5–500 字");
        String ct = blankToNull(contact);
        if (ct != null && ct.length() > MAX_CONTACT) throw new DomainException("联系方式最多 50 字");
        List<String> imgs = images == null ? List.of() : images.stream().filter(s -> s != null && !s.isBlank())
                .map(String::trim).toList();
        if (imgs.size() > MAX_IMAGES) throw new DomainException("最多上传 3 张图片");
        imgs.forEach(UploadedImageUrl::new);
        String ci = truncate(blankToNull(clientInfo), MAX_CLIENT_INFO);
        String ref = truncate(blankToNull(sourceRef), MAX_SOURCE_REF);
        return new Feedback(null, memberId, type, ref, c, ct, imgs, ci, FeedbackStatus.NEW, null, null, null, now);
    }

    public static Feedback rehydrate(Long id, Long memberId, String content, String contact, List<String> images,
                                     String clientInfo, FeedbackStatus status, LocalDateTime createTime) {
        return new Feedback(id, memberId, FeedbackType.GENERAL, null, content, contact, images, clientInfo, status,
                null, null, null, createTime);
    }

    public static Feedback rehydrate(Long id, Long memberId, FeedbackType type, String sourceRef, String content,
                                     String contact, List<String> images, String clientInfo, FeedbackStatus status,
                                     String reply, LocalDateTime repliedAt, Long repliedBy, LocalDateTime createTime) {
        return new Feedback(id, memberId, type, sourceRef, content, contact, images, clientInfo, status, reply,
                repliedAt, repliedBy, createTime);
    }

    /**
     * 运营回复（可再次修改回复）：NEW → REPLIED；已 DONE 的保持 DONE。
     *
     * @return 去首尾空白后的回复内容
     */
    public String replyBy(Long adminId, String text, LocalDateTime now) {
        if (adminId == null) throw new DomainException("请先登录");
        String r = text == null ? "" : text.trim();
        if (r.isEmpty()) throw new DomainException("回复内容不能为空");
        if (r.length() > MAX_REPLY) throw new DomainException("回复最多 1000 字");
        this.reply = r;
        this.repliedAt = now;
        this.repliedBy = adminId;
        if (status == FeedbackStatus.NEW) status = FeedbackStatus.REPLIED;
        return r;
    }

    /** 运营改状态：REPLIED 只能在已有回复时设置。 */
    public void changeStatus(FeedbackStatus newStatus) {
        if (newStatus == null) throw new DomainException("反馈状态不合法");
        if (newStatus == FeedbackStatus.REPLIED && reply == null) throw new DomainException("还没有回复，不能标记已回复");
        this.status = newStatus;
    }

    public boolean ownedBy(Long member) {
        return member != null && member.equals(memberId);
    }

    public void identify(Long newId) {
        if (this.id != null && !this.id.equals(newId)) throw new DomainException("聚合 id 不可变更");
        this.id = newId;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String truncate(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max) : s;
    }

    @Override
    public Long id() {
        return id;
    }

    public Long memberId() {
        return memberId;
    }

    public FeedbackType type() {
        return type;
    }

    public String sourceRef() {
        return sourceRef;
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

    public String reply() {
        return reply;
    }

    public LocalDateTime repliedAt() {
        return repliedAt;
    }

    public Long repliedBy() {
        return repliedBy;
    }

    public LocalDateTime createTime() {
        return createTime;
    }
}
