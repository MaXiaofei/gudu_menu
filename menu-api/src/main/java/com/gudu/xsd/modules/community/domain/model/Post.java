package com.gudu.xsd.modules.community.domain.model;

import com.gudu.xsd.common.domain.AggregateRoot;
import com.gudu.xsd.common.domain.DomainException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 厨房圈帖子（聚合根）。
 *
 * <p>生命周期：发布 → 预审（{@link #applyVerdict}）→ PUBLISHED / REVIEWING；运营处置（{@link #moderate}）；
 * 举报达阈值自动隐藏（{@link #hideByReports}）；作者删除（{@link #deleteBy}）。
 * 可见性：作者本人总能看到自己的帖子（含审核中/被隐藏）；他人只看到 PUBLISHED，且 FOLLOWERS 帖要求已关注作者。
 *
 * <p>图片：只展示本站图片（{@link SiteImage}）；站外图片链接被拦截——不展示，记录在 blockedImages 供运营查看，
 * 且整帖判为高风险进入人工审核。
 */
public final class Post extends AggregateRoot<Long> {

    public static final int MAX_TITLE = 30;
    public static final int MAX_TEXT = 1000;
    public static final int MAX_IMAGES = 9;
    public static final int MAX_TOPICS = 3;
    public static final double MIN_RATIO = 0.5;
    public static final double MAX_RATIO = 2.0;
    static final int MAX_URL = 255;
    private static final Pattern HTTP_URL = Pattern.compile("^https?://[^\\s\"'<>]+$");

    private Long id;
    private final Long authorId;
    private final String title;
    private final String text;
    private final List<String> images;
    private final List<String> blockedImages;
    private final Long dishId;
    private final List<Long> topicIds;
    private final PostVisibility visibility;
    private final Long fromCookRecordId;
    private final String city;
    /** 封面宽高比（宽/高），瀑布流排版用；客户端未传时为 1。 */
    private final double coverRatio;
    private final LocalDateTime createTime;
    private PostStatus status;
    private ModerationState moderation;
    private PostCounts counts;

    private Post(Long id, Long authorId, String title, String text, List<String> images, List<String> blockedImages,
                 Long dishId, List<Long> topicIds, PostVisibility visibility, Long fromCookRecordId, String city,
                 double coverRatio, LocalDateTime createTime, PostStatus status, ModerationState moderation,
                 PostCounts counts) {
        this.id = id;
        this.authorId = authorId;
        this.title = title;
        this.text = text == null ? "" : text;
        this.images = images == null ? List.of() : List.copyOf(images);
        this.blockedImages = blockedImages == null ? List.of() : List.copyOf(blockedImages);
        this.dishId = dishId;
        this.topicIds = topicIds == null ? List.of() : List.copyOf(topicIds);
        this.visibility = visibility == null ? PostVisibility.PUBLIC : visibility;
        this.fromCookRecordId = fromCookRecordId;
        this.city = city;
        this.coverRatio = clampRatio(coverRatio);
        this.createTime = createTime;
        this.status = status == null ? PostStatus.REVIEWING : status;
        this.moderation = moderation == null ? ModerationState.PENDING : moderation;
        this.counts = counts == null ? PostCounts.ZERO : counts;
    }

    /** 发布：校验标题/正文/图片/话题；站外图片拦截入 blockedImages；初始为审核中，等预审结论落定。 */
    public static Post publish(Long authorId, String title, String text, List<String> images, Long dishId,
                               List<Long> topicIds, PostVisibility visibility, Long fromCookRecordId,
                               String city, Double coverRatio, LocalDateTime now) {
        if (authorId == null) throw new DomainException("请先登录");
        String t = title == null ? "" : title.trim();
        if (t.isEmpty() || t.length() > MAX_TITLE) throw new DomainException("标题需 1–30 字");
        String body = text == null ? "" : text.trim();
        if (body.length() > MAX_TEXT) throw new DomainException("正文最多 1000 字");
        List<String> all = images == null ? List.of() : images.stream().filter(Objects::nonNull)
                .map(String::trim).filter(s -> !s.isEmpty()).toList();
        if (all.size() > MAX_IMAGES) throw new DomainException("最多 9 张图片");
        List<String> kept = new ArrayList<>();
        List<String> blocked = new ArrayList<>();
        for (String u : all) {
            if (SiteImage.isValid(u)) {
                kept.add(u);
            } else if (u.length() <= MAX_URL && HTTP_URL.matcher(u).matches()) {
                blocked.add(u);
            } else {
                throw new DomainException("图片地址不合法");
            }
        }
        if (kept.isEmpty()) throw new DomainException("至少放 1 张本站上传的图片");
        List<Long> topics = topicIds == null ? List.of() : topicIds.stream().filter(Objects::nonNull).distinct().toList();
        if (topics.size() > MAX_TOPICS) throw new DomainException("最多选 3 个话题");
        return new Post(null, authorId, t, body, kept, blocked, dishId, topics, visibility, fromCookRecordId, city,
                coverRatio == null ? 1.0 : coverRatio, now, PostStatus.REVIEWING, ModerationState.PENDING,
                PostCounts.ZERO);
    }

    public static Post rehydrate(Long id, Long authorId, String title, String text, List<String> images,
                                 List<String> blockedImages, Long dishId, List<Long> topicIds,
                                 PostVisibility visibility, Long fromCookRecordId, String city, double coverRatio,
                                 LocalDateTime createTime, PostStatus status, ModerationState moderation,
                                 PostCounts counts) {
        return new Post(id, authorId, title, text, images, blockedImages, dishId, topicIds, visibility,
                fromCookRecordId, city, coverRatio, createTime, status, moderation, counts);
    }

    /**
     * 落定预审结论：高风险进人工队列（他人不可见）；中低风险直接公开，中风险留待复核。
     * 含被拦截的站外图片时一律高风险。
     */
    public void applyVerdict(ModerationVerdict verdict) {
        if (status == PostStatus.DELETED) return;
        ModerationVerdict v = verdict == null ? ModerationVerdict.low() : verdict;
        RiskLevel risk = v.risk();
        List<String> reasons = new ArrayList<>(v.reasons());
        List<RiskScore> scores = new ArrayList<>(v.scores());
        if (!blockedImages.isEmpty()) {
            risk = RiskLevel.HIGH;
            reasons.add(0, "外链图片（已拦截 " + blockedImages.size() + " 张）");
            scores.add(0, new RiskScore("外链图片", 1.0));
        }
        this.moderation = new ModerationState(risk, reasons, scores, risk == RiskLevel.LOW);
        this.status = risk == RiskLevel.HIGH ? PostStatus.REVIEWING : PostStatus.PUBLISHED;
    }

    /** 运营处置：通过 → 公开；隐藏 / 封禁作者 → 隐藏。处置后视为已复核。 */
    public void moderate(ModerationAction action) {
        if (action == null) throw new DomainException("处置动作不合法");
        if (status == PostStatus.DELETED) throw new DomainException("帖子已被作者删除");
        this.status = action == ModerationAction.APPROVE ? PostStatus.PUBLISHED : PostStatus.HIDDEN;
        List<String> reasons = new ArrayList<>(moderation.reasons());
        reasons.remove(REPORT_HIDDEN_REASON);
        reasons.remove(MANUAL_HIDDEN_REASON);
        if (status == PostStatus.HIDDEN) reasons.add(MANUAL_HIDDEN_REASON);
        this.moderation = new ModerationState(moderation.risk(), reasons, moderation.scores(), true);
    }

    /** 举报达到阈值：自动隐藏并回到人工复核队列。返回是否发生了状态变化。 */
    public boolean hideByReports() {
        if (status == PostStatus.HIDDEN && imageBlocked() && !moderation.reasons().contains(REPORT_HIDDEN_REASON)) {
            // 已因图片被隐藏：登记举报原因，之后图片复核恢复也不会让帖子绕过举报重新公开
            this.moderation = withReason(moderation.backToQueue(), REPORT_HIDDEN_REASON);
            return true;
        }
        if (status != PostStatus.PUBLISHED) return false;
        this.status = PostStatus.HIDDEN;
        this.moderation = withReason(moderation.backToQueue(), REPORT_HIDDEN_REASON);
        return true;
    }

    private static ModerationState withReason(ModerationState m, String reason) {
        List<String> reasons = new ArrayList<>(m.reasons());
        if (!reasons.contains(reason)) reasons.add(reason);
        return new ModerationState(m.risk(), reasons, m.scores(), m.reviewed());
    }

    /** 隐藏原因：举报达阈值 / 运营处置隐藏（与「图片未通过审核」并存时，图片复核恢复不得把帖子改回公开）。 */
    public static final String REPORT_HIDDEN_REASON = "举报达阈值";
    public static final String MANUAL_HIDDEN_REASON = "运营处置隐藏";

    /** 是否还有图片之外的隐藏原因（举报 / 人工处置）。 */
    public boolean hiddenByOthers() {
        return moderation.reasons().contains(REPORT_HIDDEN_REASON)
                || moderation.reasons().contains(MANUAL_HIDDEN_REASON);
    }

    /** 内容安全：图片未通过审核时记入审核原因、展示给作者的提示。 */
    public static final String IMAGE_BLOCKED_REASON = "图片未通过审核";

    /**
     * 帖子里有图片被内容安全判违规：隐藏（仅作者可见）并回到人工复核队列（与举报隐藏同一状态），
     * 审核原因记「图片未通过审核」。返回是否发生了变化（已删除 / 已记过的不重复处理）。
     */
    public boolean hideForBlockedImage() {
        if (status == PostStatus.DELETED) return false;
        if (status == PostStatus.HIDDEN && imageBlocked()) return false;
        List<String> reasons = new ArrayList<>(moderation.reasons());
        if (!reasons.contains(IMAGE_BLOCKED_REASON)) reasons.add(0, IMAGE_BLOCKED_REASON);
        this.moderation = new ModerationState(RiskLevel.HIGH, reasons, moderation.scores(), false);
        this.status = PostStatus.HIDDEN;
        return true;
    }

    /**
     * 违规图片经人工复核为误判（F.3）：去掉「图片未通过审核」原因；若因此被隐藏且没有其他隐藏原因（举报 / 人工处置）则恢复公开（视为已复核），否则保持隐藏。
     * 返回是否发生了变化（未因图片被处置 / 已删除的不处理）。
     */
    public boolean restoreFromBlockedImage() {
        if (status == PostStatus.DELETED || !imageBlocked()) return false;
        List<String> reasons = new ArrayList<>(moderation.reasons());
        reasons.remove(IMAGE_BLOCKED_REASON);
        RiskLevel risk = reasons.isEmpty() ? RiskLevel.LOW : moderation.risk();
        boolean others = hiddenByOthers();
        // 仍有举报 / 人工处置等其他隐藏原因：只去掉图片原因，保持隐藏、留在复核队列
        this.moderation = new ModerationState(risk, reasons, moderation.scores(), !others);
        if (status == PostStatus.HIDDEN && !others) this.status = PostStatus.PUBLISHED;
        return true;
    }

    /** 是否因图片违规被处置过。 */
    public boolean imageBlocked() {
        return moderation.reasons().contains(IMAGE_BLOCKED_REASON);
    }

    /** 申诉前置条件：只有作者能对「被隐藏」的帖子申诉（一次性由仓储唯一键保证）。 */
    public void checkAppealable(Long userId) {
        if (!isAuthor(userId)) throw new DomainException("只能对自己的帖子申诉");
        if (status != PostStatus.HIDDEN) throw new DomainException("只有被隐藏的帖子可以申诉");
    }

    public void deleteBy(Long userId) {
        if (!isAuthor(userId)) throw new DomainException("只能删除自己的帖子");
        this.status = PostStatus.DELETED;
    }

    public boolean isAuthor(Long userId) {
        return userId != null && userId.equals(authorId);
    }

    /** 他人可见性判定；followsAuthor 仅对 FOLLOWERS 帖有意义。 */
    public boolean visibleTo(Long viewerId, boolean followsAuthor) {
        if (status == PostStatus.DELETED) return false;
        if (isAuthor(viewerId)) return true;
        if (status != PostStatus.PUBLISHED) return false;
        return visibility == PostVisibility.PUBLIC || followsAuthor;
    }

    public void identify(Long newId) {
        if (this.id != null && !this.id.equals(newId)) throw new DomainException("聚合 id 不可变更");
        this.id = newId;
    }

    private static double clampRatio(double r) {
        if (Double.isNaN(r) || r <= 0) return 1.0;
        return Math.max(MIN_RATIO, Math.min(MAX_RATIO, r));
    }

    public String cover() {
        return images.isEmpty() ? null : images.get(0);
    }

    @Override
    public Long id() {
        return id;
    }

    public Long authorId() {
        return authorId;
    }

    public String title() {
        return title;
    }

    public String text() {
        return text;
    }

    public List<String> images() {
        return images;
    }

    public List<String> blockedImages() {
        return blockedImages;
    }

    public Long dishId() {
        return dishId;
    }

    public List<Long> topicIds() {
        return topicIds;
    }

    public PostVisibility visibility() {
        return visibility;
    }

    public Long fromCookRecordId() {
        return fromCookRecordId;
    }

    public String city() {
        return city;
    }

    public double coverRatio() {
        return coverRatio;
    }

    public LocalDateTime createTime() {
        return createTime;
    }

    public PostStatus status() {
        return status;
    }

    public ModerationState moderation() {
        return moderation;
    }

    public RiskLevel risk() {
        return moderation.risk();
    }

    public List<String> riskReasons() {
        return moderation.reasons();
    }

    public boolean reviewed() {
        return moderation.reviewed();
    }

    public PostCounts counts() {
        return counts;
    }
}
