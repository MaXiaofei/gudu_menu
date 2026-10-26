package com.gudu.xsd.modules.community.application;

import com.gudu.xsd.common.BizException;
import com.gudu.xsd.modules.community.application.dto.CommunityViews.AiText;
import com.gudu.xsd.modules.community.application.dto.CommunityViews.CommentView;
import com.gudu.xsd.modules.community.application.dto.CommunityViews.CopyItem;
import com.gudu.xsd.modules.community.application.dto.CommunityViews.CopyResult;
import com.gudu.xsd.modules.community.application.dto.CommunityViews.CursorPage;
import com.gudu.xsd.modules.community.application.dto.CommunityViews.DishCard;
import com.gudu.xsd.modules.community.application.dto.CommunityViews.Items;
import com.gudu.xsd.modules.community.application.dto.CommunityViews.PostCard;
import com.gudu.xsd.modules.community.application.dto.CommunityViews.PostDetail;
import com.gudu.xsd.modules.community.application.dto.CommunityViews.PublishResult;
import com.gudu.xsd.modules.community.application.dto.CommunityViews.ReactionResult;
import com.gudu.xsd.modules.community.application.dto.CommunityViews.RecipeCard;
import com.gudu.xsd.modules.community.application.dto.CommunityViews.TopicRef;
import com.gudu.xsd.modules.community.application.dto.CommunityViews.UserLite;
import com.gudu.xsd.modules.community.domain.model.Comment;
import com.gudu.xsd.modules.community.domain.model.Post;
import com.gudu.xsd.modules.community.domain.model.PostCounter;
import com.gudu.xsd.modules.community.domain.model.PostCounts;
import com.gudu.xsd.modules.community.domain.model.PostStatus;
import com.gudu.xsd.modules.community.domain.model.PostVisibility;
import com.gudu.xsd.modules.community.domain.model.ReactionType;
import com.gudu.xsd.modules.community.domain.model.SiteImage;
import com.gudu.xsd.modules.community.domain.model.Topic;
import com.gudu.xsd.modules.community.domain.repository.AppealRepository;
import com.gudu.xsd.modules.community.domain.repository.CommentRepository;
import com.gudu.xsd.modules.community.domain.repository.FollowInbox;
import com.gudu.xsd.modules.community.domain.repository.InteractionRepository;
import com.gudu.xsd.modules.community.domain.repository.ModerationRepository;
import com.gudu.xsd.modules.community.domain.repository.PostCounterStore;
import com.gudu.xsd.modules.community.domain.repository.PostRepository;
import com.gudu.xsd.modules.community.domain.repository.TopicRepository;
import com.gudu.xsd.modules.community.domain.service.PostModerator;
import com.gudu.xsd.modules.community.domain.service.PostTextWriter;
import com.gudu.xsd.modules.community.domain.service.ReportPolicy;
import com.gudu.xsd.modules.contentsecurity.application.ContentSecurityAppService;
import com.gudu.xsd.modules.contentsecurity.domain.model.ContentPurpose;
import com.gudu.xsd.modules.cookbook.application.CookingRecordAppService;
import com.gudu.xsd.modules.cookbook.domain.model.CookedRecordRef;
import com.gudu.xsd.modules.dish.Dish;
import com.gudu.xsd.modules.dish.DishSaveDTO;
import com.gudu.xsd.modules.dish.DishService;
import com.gudu.xsd.modules.notification.NotificationPayload;
import com.gudu.xsd.modules.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 厨房圈帖子用例：发布（同步禁发检查 + 规则预审 + 写扩散 + 挑战打卡）、详情、删除、赞/收藏、照着做、评论、举报、AI 帮写。
 * 身份 = 登录 id（member.id）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommunityPostAppService {

    /** 粉丝数超过该值的作者不写扩散（读时合并）。 */
    public static final long BIG_V_FOLLOWERS = 5000;
    static final int COMMENT_PAGE = 20;
    static final int MAX_REPORT_REASON = 100;
    static final String NOTIFY_TYPE = "community";

    private final PostRepository posts;
    private final CommentRepository comments;
    private final InteractionRepository interactions;
    private final TopicRepository topics;
    private final ModerationRepository moderation;
    private final AppealRepository appeals;
    private final FollowInbox inbox;
    private final PostCounterStore counters;
    private final PostModerator moderator;
    private final PostTextWriter textWriter;
    private final CommunityAssembler assembler;
    private final DishService dishService;
    private final CookingRecordAppService cookingRecords;
    private final NotificationService notificationService;
    private final Clock clock;
    private final ContentSecurityAppService contentSecurity;

    // ============================ 发布 ============================

    public record PublishCommand(String title, String text, List<String> imageUrls, Long dishId, List<Long> topicIds,
                                 String visibility, Long fromCookRecordId, Long fromFoodLogId, Double coverRatio,
                                 String city) {
    }

    @Transactional(rollbackFor = Exception.class)
    public PublishResult publish(Long userId, PublishCommand cmd) {
        requireLogin(userId);
        // DATETIME 精度到秒且会四舍五入：先截断，保证库里时间不晚于本次请求时刻（Feed 的 asOf 截断依赖这一点）
        LocalDateTime now = LocalDateTime.now(clock).withNano(0);
        moderation.bannedUntil(userId).filter(until -> until.isAfter(now)).ifPresent(until -> {
            throw BizException.forbidden("你暂时不能发布内容（至 " + until.toLocalDate() + "）");
        });
        Long dishId = cmd.dishId();
        List<String> images = cmd.imageUrls() == null ? new ArrayList<>() : new ArrayList<>(cmd.imageUrls());
        Long sourceRecordId = cmd.fromCookRecordId() != null ? cmd.fromCookRecordId() : cmd.fromFoodLogId();
        if (sourceRecordId != null) {
            // 从食记分享：只带入菜和图片，不带成员名、备注等家庭信息。
            // fromCookRecordId = 单条做菜记录；fromFoodLogId = 食记（一顿饭，可能多道菜）的代表记录 id。
            List<CookedRecordRef> refs = cmd.fromCookRecordId() != null
                    ? cookingRecords.refOf(sourceRecordId).map(List::of).orElse(List.of())
                    : cookingRecords.sameMealRefs(sourceRecordId);
            if (refs.isEmpty()) throw new BizException("食记不存在");
            if (refs.stream().anyMatch(r -> !userId.equals(r.memberId()))) {
                throw BizException.forbidden("只能分享自己的食记");
            }
            if (dishId == null) dishId = refs.get(0).dishId();
            if (images.isEmpty()) {
                List<Long> dishIds = refs.stream().map(CookedRecordRef::dishId).filter(Objects::nonNull).distinct()
                        .limit(Post.MAX_IMAGES).toList();
                for (Long did : dishIds) {
                    Dish d = dishService.getById(did);
                    if (d != null && d.getCoverUrl() != null && SiteImage.isValid(d.getCoverUrl())) images.add(d.getCoverUrl());
                }
            }
        }
        if (dishId != null && dishService.getById(dishId) == null) throw new BizException("关联的菜谱不存在");
        List<Long> topicIds = cmd.topicIds() == null ? List.of() : cmd.topicIds();
        if (!topicIds.isEmpty() && topics.findByIds(topicIds).size() != topicIds.stream().distinct().count()) {
            throw new BizException("话题不存在");
        }
        // 内容安全（A.6）：禁发词规则先行 + 有 openid 时微信 msgSecCheck；微信不可用降级到规则
        contentSecurity.assertText(userId, ContentPurpose.POST, joinText(cmd.title(), cmd.text()));
        Post post = Post.publish(userId, cmd.title(), cmd.text(), images, dishId, topicIds,
                PostVisibility.of(cmd.visibility()), sourceRecordId, cmd.city(), cmd.coverRatio(), now);
        post.applyVerdict(moderator.review(post.title(), post.text(), post.images()));
        posts.save(post);
        if (post.status() == PostStatus.PUBLISHED) onPublished(post);
        return new PublishResult(post.id(), post.status().name());
    }

    /** 帖子对外可见时：写扩散到粉丝收件箱 + 作者自己的收件箱；带挑战话题的记一次当天打卡。 */
    void onPublished(Post post) {
        long ms = epochMs(post.createTime());
        List<Long> targets = new ArrayList<>();
        targets.add(post.authorId());
        if (interactions.followerCount(post.authorId()) <= BIG_V_FOLLOWERS) {
            targets.addAll(interactions.followers(post.authorId()));
        }
        inbox.push(targets, post.id(), ms);
        LocalDate day = post.createTime().toLocalDate();
        for (Topic t : topics.findByIds(post.topicIds())) {
            if (t.challenge() && t.activeOn(day)) topics.checkin(t.id(), post.authorId(), day, post.id());
        }
    }

    // ============================ 详情 / 删除 ============================

    public PostDetail detail(Long viewerId, Long postId) {
        Post p = visiblePost(viewerId, postId);
        PostCard card = assembler.cards(List.of(p), viewerId).get(0);
        PostCounts c = assembler.counts(p);
        boolean collected = viewerId != null
                && interactions.reactedPostIds(viewerId, List.of(p.id()), ReactionType.COLLECT).contains(p.id());
        boolean following = viewerId != null && !p.isAuthor(viewerId) && interactions.isFollowing(viewerId, p.authorId());
        Map<Long, String> names = assembler.topicNames(p.topicIds());
        List<TopicRef> topicRefs = p.topicIds().stream().filter(names::containsKey)
                .map(id -> new TopicRef(id, names.get(id))).toList();
        RecipeCard recipe = null;
        if (p.dishId() != null) {
            DishCard d = assembler.dishCards(List.of(p.dishId())).get(p.dishId());
            if (d != null) {
                recipe = new RecipeCard(d.id(), d.name(), d.coverUrl(), d.minutes(), d.difficulty(), d.coverage(),
                        d.tags(), c.copies());
            }
        }
        return new PostDetail(p.id(), card.author(), p.title(), p.cover(), p.coverRatio(), p.images().size(),
                c.likes(), card.liked(), c.copies(), card.tags(), p.text(), p.images(), topicRefs, recipe,
                c.comments(), c.collects(), collected, following, p.createTime(), p.city(), p.status().name(),
                statusReason(p, viewerId));
    }

    /** 给作者看的处置原因：目前只回「图片未通过审核」（其余审核原因是运营内部信息，不外露）。 */
    static String statusReason(Post p, Long viewerId) {
        return p.isAuthor(viewerId) && p.status() != PostStatus.PUBLISHED && p.imageBlocked()
                ? Post.IMAGE_BLOCKED_REASON : null;
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long userId, Long postId) {
        requireLogin(userId);
        Post p = posts.findById(postId).filter(x -> x.status() != PostStatus.DELETED)
                .orElseThrow(() -> new BizException("帖子不存在"));
        p.deleteBy(userId);
        posts.save(p);
    }

    // ============================ 赞 / 收藏 ============================

    @Transactional(rollbackFor = Exception.class)
    public ReactionResult react(Long userId, Long postId, String type, boolean on) {
        requireLogin(userId);
        Post p = visiblePost(userId, postId);
        ReactionType t = ReactionType.of(type);
        boolean changed = on ? interactions.addReaction(p.id(), userId, t) : interactions.removeReaction(p.id(), userId, t);
        if (changed) counters.incr(p.id(), t.counter(), on ? 1 : -1);
        PostCounts c = assembler.counts(p);
        List<Long> ids = List.of(p.id());
        return new ReactionResult(c.likes(), c.collects(),
                interactions.reactedPostIds(userId, ids, ReactionType.LIKE).contains(p.id()),
                interactions.reactedPostIds(userId, ids, ReactionType.COLLECT).contains(p.id()));
    }

    // ============================ 照着做 ============================

    /** 复制关联菜谱为当前用户的私有菜谱；同一用户重复点返回第一次复制出的菜（幂等）。 */
    @Transactional(rollbackFor = Exception.class)
    public CopyResult copyRecipe(Long userId, Long postId) {
        requireLogin(userId);
        Post p = visiblePost(userId, postId);
        if (p.dishId() == null) throw new BizException("这条帖子没有关联菜谱");
        Optional<Long> existing = interactions.copiedDishId(p.id(), userId);
        if (existing.isPresent() && dishService.getById(existing.get()) != null) return new CopyResult(existing.get());
        DishService.DishDetail src = dishService.detail(p.dishId());
        if (src == null || src.dish() == null) throw new BizException("关联的菜谱已不存在");
        Long newDishId = copyDish(src);
        interactions.saveCopy(p.id(), userId, newDishId);
        counters.incr(p.id(), PostCounter.COPY, 1);
        if (!p.isAuthor(userId)) {
            UserLite who = assembler.user(userId);
            notify(p.authorId(), "有人照着你的帖子做了",
                    who.nickname() + " 把「" + src.dish().getName() + "」复制到了自己家，准备照着做");
        }
        return new CopyResult(newDishId);
    }

    private Long copyDish(DishService.DishDetail src) {
        Dish s = src.dish();
        Dish d = new Dish();
        d.setName(s.getName());
        d.setNote(s.getNote());
        d.setCoverUrl(s.getCoverUrl());
        d.setPrepTime(s.getPrepTime());
        d.setCookTime(s.getCookTime());
        d.setDifficulty(s.getDifficulty());
        d.setSource("ORIGINAL");
        d.setSourceName("厨房圈");
        DishSaveDTO dto = new DishSaveDTO();
        dto.setDish(d);
        dto.setSteps(src.steps());
        dto.setIngredients(src.ingredients());
        dto.setCuisineIds(src.cuisineIds());
        dto.setTagIds(src.tagIds());
        dto.setCategoryIds(src.categoryIds());
        dishService.saveFull(dto);
        return d.getId();
    }

    /** 他们照着做了：最近照着做的人 + 他们晒的同一道菜（没晒就用菜谱封面）。 */
    public Items<CopyItem> copies(Long viewerId, Long postId, Integer size) {
        Post p = visiblePost(viewerId, postId);
        int n = size == null || size < 1 ? 10 : Math.min(size, 30);
        List<Long> copiers = interactions.recentCopiers(p.id(), n);
        if (copiers.isEmpty()) return new Items<>(List.of());
        Map<Long, UserLite> users = assembler.users(copiers);
        List<Post> theirPosts = posts.recentByAuthors(copiers, LocalDateTime.now(clock).minusDays(365), 200);
        String fallback = p.cover();
        if (p.dishId() != null) {
            Dish d = dishService.getById(p.dishId());
            if (d != null && d.getCoverUrl() != null) fallback = d.getCoverUrl();
        }
        String fb = fallback;
        return new Items<>(copiers.stream().filter(users::containsKey).map(uid -> new CopyItem(users.get(uid),
                theirPosts.stream().filter(tp -> tp.authorId().equals(uid) && Objects.equals(tp.dishId(), p.dishId()))
                        .findFirst().map(Post::cover).orElse(fb))).toList());
    }

    // ============================ 评论 ============================

    public CursorPage<CommentView> comments(Long viewerId, Long postId, String cursor, Integer size) {
        Post p = visiblePost(viewerId, postId);
        int n = size == null || size < 1 ? COMMENT_PAGE : Math.min(size, 50);
        Long afterId = parseLongCursor(cursor);
        List<Comment> top = comments.topLevel(p.id(), afterId, n + 1);
        boolean more = top.size() > n;
        List<Comment> page = more ? top.subList(0, n) : top;
        Map<Long, List<Comment>> replies = comments.replies(page.stream().map(Comment::id).toList());
        List<Long> authorIds = new ArrayList<>(page.stream().map(Comment::authorId).toList());
        replies.values().forEach(l -> l.forEach(c -> authorIds.add(c.authorId())));
        Map<Long, UserLite> users = assembler.users(authorIds);
        List<CommentView> items = page.stream().map(c -> view(c, p, users,
                replies.getOrDefault(c.id(), List.of()).stream().map(r -> view(r, p, users, List.of())).toList())).toList();
        return new CursorPage<>(items, more ? String.valueOf(page.get(page.size() - 1).id()) : null);
    }

    @Transactional(rollbackFor = Exception.class)
    public CommentView comment(Long userId, Long postId, String text, Long parentId) {
        requireLogin(userId);
        Post p = visiblePost(userId, postId);
        Comment parent = parentId == null ? null : comments.findById(parentId)
                .orElseThrow(() -> new BizException("回复的评论不存在"));
        contentSecurity.assertText(userId, ContentPurpose.COMMENT, text);
        Comment c = Comment.write(p.id(), userId, text, parent, LocalDateTime.now(clock).withNano(0));
        comments.save(c);
        counters.incr(p.id(), PostCounter.COMMENT, 1);
        UserLite me = assembler.user(userId);
        if (!p.isAuthor(userId)) notify(p.authorId(), "你的帖子有新评论", me.nickname() + "：" + abbreviate(c.text()));
        if (parent != null && !parent.authorId().equals(userId) && !p.isAuthor(parent.authorId())) {
            notify(parent.authorId(), "你的评论有新回复", me.nickname() + "：" + abbreviate(c.text()));
        }
        return view(c, p, Map.of(userId, me), List.of());
    }

    private static CommentView view(Comment c, Post p, Map<Long, UserLite> users, List<CommentView> replies) {
        return new CommentView(c.id(), users.getOrDefault(c.authorId(), new UserLite(c.authorId(), "厨友", null)),
                c.text(), p.isAuthor(c.authorId()), 0, c.createTime(), replies);
    }

    // ============================ 举报 ============================

    /** 举报：同一用户对同一帖只计一次；不同用户达阈值自动隐藏并回到人工复核队列。 */
    @Transactional(rollbackFor = Exception.class)
    public void report(Long userId, Long postId, String reason) {
        requireLogin(userId);
        Post p = posts.findById(postId).filter(x -> x.status() != PostStatus.DELETED)
                .orElseThrow(() -> new BizException("帖子不存在"));
        if (p.isAuthor(userId)) throw new BizException("不能举报自己的帖子");
        String r = reason == null || reason.isBlank() ? "其他" : reason.trim();
        if (r.length() > MAX_REPORT_REASON) r = r.substring(0, MAX_REPORT_REASON);
        boolean added = interactions.addReport(p.id(), userId, r);
        if (added && ReportPolicy.shouldAutoHide(interactions.reportCount(p.id())) && p.hideByReports()) {
            posts.save(p);
            log.info("帖子 {} 被举报达阈值，已自动隐藏", p.id());
        }
    }

    // ============================ 申诉 ============================

    /** 被隐藏帖子的作者申诉（每帖一次），进入后台「申诉」队列。 */
    @Transactional(rollbackFor = Exception.class)
    public void appeal(Long userId, Long postId, String text) {
        requireLogin(userId);
        Post p = posts.findById(postId).orElseThrow(() -> new BizException("帖子不存在"));
        p.checkAppealable(userId);
        String t = text == null ? "" : text.trim();
        if (t.isEmpty() || t.length() > AppealRepository.MAX_TEXT) throw new BizException("申诉理由需 1–300 字");
        if (!appeals.add(p.id(), userId, t, LocalDateTime.now(clock).withNano(0))) {
            throw new BizException("这条帖子已经申诉过了");
        }
    }

    // ============================ AI 帮写 ============================

    public AiText aiText(Long userId, Long dishId, Long cookRecordId, String style) {
        requireLogin(userId);
        Long did = dishId;
        String when = null;
        if (cookRecordId != null) {
            CookedRecordRef ref = cookingRecords.refOf(cookRecordId).orElseThrow(() -> new BizException("食记不存在"));
            if (!userId.equals(ref.memberId())) throw BizException.forbidden("只能使用自己的食记");
            if (did == null) did = ref.dishId();
            when = describeWhen(ref.cookedAt());
        }
        Dish d = did == null ? null : dishService.getById(did);
        Integer minutes = d == null ? null : CommunityAssembler.minutes(d);
        String diff = d == null ? null : CommunityAssembler.difficulty(d.getDifficulty());
        String text = textWriter.write(new PostTextWriter.Material(d == null ? null : d.getName(), minutes, when, diff),
                style);
        return new AiText(text);
    }

    private String describeWhen(LocalDateTime t) {
        if (t == null) return null;
        LocalDate today = LocalDate.now(clock);
        String hm = t.format(DateTimeFormatter.ofPattern("HH:mm"));
        if (t.toLocalDate().equals(today)) return "今天 " + hm;
        if (t.toLocalDate().equals(today.minusDays(1))) return "昨天 " + hm;
        return t.format(DateTimeFormatter.ofPattern("M 月 d 日"));
    }

    // ============================ 内部 ============================

    /** 对 viewer 可见的帖子，否则 404 文案（不区分不存在/无权，避免探测）。 */
    Post visiblePost(Long viewerId, Long postId) {
        Post p = posts.findById(postId).orElseThrow(() -> new BizException("帖子不存在或已隐藏"));
        boolean follows = p.visibility() == PostVisibility.FOLLOWERS && viewerId != null
                && interactions.isFollowing(viewerId, p.authorId());
        if (!p.visibleTo(viewerId, follows)) throw new BizException("帖子不存在或已隐藏");
        return p;
    }

    private void notify(Long memberId, String title, String content) {
        try {
            notificationService.send(new NotificationPayload(memberId, NOTIFY_TYPE, title, content), "in_app");
        } catch (RuntimeException e) {
            log.warn("厨房圈通知发送失败 memberId={}: {}", memberId, e.getMessage());
        }
    }

    long epochMs(LocalDateTime t) {
        ZoneId zone = clock.getZone();
        return t.atZone(zone).toInstant().toEpochMilli();
    }

    static Long parseLongCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) return null;
        try {
            return Long.parseLong(cursor);
        } catch (NumberFormatException e) {
            throw new BizException("游标不合法，请刷新");
        }
    }

    static void requireLogin(Long userId) {
        if (userId == null) throw new BizException(401, "请先登录");
    }

    private static String abbreviate(String s) {
        return s.length() <= 40 ? s : s.substring(0, 40) + "…";
    }

    private static String joinText(String title, String text) {
        return (title == null ? "" : title) + "\n" + (text == null ? "" : text);
    }
}
