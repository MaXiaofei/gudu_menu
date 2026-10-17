package com.gudu.xsd.modules.community.application;

import com.gudu.xsd.common.BizException;
import com.gudu.xsd.modules.community.application.dto.CommunityViews.AdminPostDetail;
import com.gudu.xsd.modules.community.application.dto.CommunityViews.AdminStats;
import com.gudu.xsd.modules.community.application.dto.CommunityViews.AppealView;
import com.gudu.xsd.modules.community.application.dto.CommunityViews.AuthorHistory;
import com.gudu.xsd.modules.community.application.dto.CommunityViews.CursorPage;
import com.gudu.xsd.modules.community.application.dto.CommunityViews.DishCard;
import com.gudu.xsd.modules.community.application.dto.CommunityViews.ModerationView;
import com.gudu.xsd.modules.community.application.dto.CommunityViews.QueueItem;
import com.gudu.xsd.modules.community.application.dto.CommunityViews.ReportView;
import com.gudu.xsd.modules.community.application.dto.CommunityViews.ScoreView;
import com.gudu.xsd.modules.community.application.dto.CommunityViews.TopicRef;
import com.gudu.xsd.modules.community.application.dto.CommunityViews.UserLite;
import com.gudu.xsd.modules.community.domain.model.FeedCursor;
import com.gudu.xsd.modules.community.domain.model.ModerationAction;
import com.gudu.xsd.modules.community.domain.model.ModerationState;
import com.gudu.xsd.modules.community.domain.model.Post;
import com.gudu.xsd.modules.community.domain.model.PostCounts;
import com.gudu.xsd.modules.community.domain.model.PostStatus;
import com.gudu.xsd.modules.community.domain.model.ReviewStatus;
import com.gudu.xsd.modules.community.domain.model.RiskLevel;
import com.gudu.xsd.modules.community.domain.repository.AppealRepository;
import com.gudu.xsd.modules.community.domain.repository.AppealRepository.Appeal;
import com.gudu.xsd.modules.community.domain.repository.InteractionRepository;
import com.gudu.xsd.modules.community.domain.repository.InteractionRepository.Report;
import com.gudu.xsd.modules.community.domain.repository.ModerationRepository;
import com.gudu.xsd.modules.community.domain.repository.ModerationRepository.LogEntry;
import com.gudu.xsd.modules.community.domain.repository.PostRepository;
import com.gudu.xsd.modules.community.domain.service.FeedPager;
import com.gudu.xsd.modules.community.domain.service.ModerationSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 社区审核后台：审核队列、统计、管理员视角详情、处置（通过 / 隐藏 / 封禁 7 天，均写审计日志）。 */
@Service
@RequiredArgsConstructor
public class CommunityAdminAppService {

    public static final int BAN_DAYS = 7;
    static final int MAX_NOTE = 300;
    static final int MAX_REASON = 50;
    static final int QUEUE_REPORTS = 5;

    private final PostRepository posts;
    private final InteractionRepository interactions;
    private final ModerationRepository moderation;
    private final AppealRepository appeals;
    private final CommunityAssembler assembler;
    private final CommunityPostAppService postAppService;
    private final Clock clock;

    public CursorPage<QueueItem> queue(String status, String risk, String cursor, Integer size) {
        int n = FeedPager.normalizeSize(size);
        ReviewStatus st = ReviewStatus.of(status);
        RiskLevel r = risk == null || risk.isBlank() ? null : parseRisk(risk);
        FeedCursor c = FeedCursor.decode(cursor);
        LocalDateTime before = c == null ? null
                : LocalDateTime.ofInstant(Instant.ofEpochMilli((long) c.score()), clock.getZone());
        List<Post> rows = posts.reviewQueue(st, r, before, c == null ? null : c.id(), n + 1);
        boolean more = rows.size() > n;
        List<Post> page = more ? rows.subList(0, n) : rows;
        List<Long> ids = page.stream().map(Post::id).toList();
        Map<Long, List<Report>> reports = interactions.reportsOf(ids);
        Map<Long, LogEntry> latest = moderation.latestOf(ids);
        Map<Long, Appeal> appealMap = appeals.findByPosts(ids);
        List<Long> userIds = new ArrayList<>(page.stream().map(Post::authorId).toList());
        reports.values().forEach(l -> l.forEach(x -> userIds.add(x.reporterId())));
        latest.values().forEach(l -> userIds.add(l.operatorId()));
        Map<Long, UserLite> users = assembler.users(userIds);
        Map<Long, AuthorHistory> histories = new HashMap<>();
        List<QueueItem> items = page.stream().map(p -> {
            List<Report> rs = reports.getOrDefault(p.id(), List.of());
            return new QueueItem(p.id(), p.cover(), p.title(), user(users, p.authorId()), p.risk().name(),
                    p.riskReasons(), rs.size(), p.status().name(), p.createTime(), scores(p.moderation()),
                    ModerationSummary.of(p.moderation(), rs.size()),
                    rs.stream().limit(QUEUE_REPORTS).map(x -> reportView(x, users)).toList(),
                    moderationView(latest.get(p.id()), users), appealView(appealMap.get(p.id())),
                    histories.computeIfAbsent(p.authorId(), this::history));
        }).toList();
        String next = null;
        if (more) {
            Post last = page.get(page.size() - 1);
            next = new FeedCursor(last.createTime().atZone(clock.getZone()).toInstant().toEpochMilli(), last.id(),
                    clock.millis()).encode();
        }
        return new CursorPage<>(items, next);
    }

    public AdminStats stats() {
        LocalDateTime today = LocalDate.now(clock).atStartOfDay();
        long created = posts.countCreatedSince(today);
        long autoPassed = posts.countCreatedSinceWithRisk(today, RiskLevel.LOW);
        double rate = created == 0 ? 1.0 : Math.round(autoPassed * 1000.0 / created) / 1000.0;
        Double avg = moderation.avgHandleMinutesSince(today).map(v -> Math.round(v * 10) / 10.0).orElse(null);
        return new AdminStats(created, posts.countFromCookSince(today), rate, posts.countPendingReview(null),
                posts.countPendingReview(RiskLevel.HIGH), posts.countPendingReview(RiskLevel.MEDIUM),
                interactions.reportsSince(today), posts.countAutoHiddenPending(), appeals.countPending(), avg);
    }

    /** 管理员视角详情：不受可见性限制（含 REVIEWING / HIDDEN / DELETED）。 */
    public AdminPostDetail postDetail(Long postId) {
        Post p = posts.findById(postId).orElseThrow(() -> new BizException("帖子不存在"));
        List<Report> rs = interactions.reportsOf(List.of(p.id())).getOrDefault(p.id(), List.of());
        List<LogEntry> logs = moderation.logsOf(p.id());
        List<Long> userIds = new ArrayList<>();
        userIds.add(p.authorId());
        rs.forEach(x -> userIds.add(x.reporterId()));
        logs.forEach(l -> userIds.add(l.operatorId()));
        Map<Long, UserLite> users = assembler.users(userIds);
        DishCard dish = p.dishId() == null ? null : assembler.dishCards(List.of(p.dishId())).get(p.dishId());
        Map<Long, String> names = assembler.topicNames(p.topicIds());
        List<TopicRef> topics = p.topicIds().stream().filter(names::containsKey)
                .map(id -> new TopicRef(id, names.get(id))).toList();
        PostCounts c = assembler.counts(p);
        return new AdminPostDetail(p.id(), user(users, p.authorId()), p.title(), p.text(), p.images(),
                p.blockedImages(), p.coverRatio(), dish, topics, p.visibility().name(), p.status().name(),
                p.risk().name(), p.riskReasons(), scores(p.moderation()), ModerationSummary.of(p.moderation(), rs.size()),
                p.reviewed(), rs.size(), rs.stream().map(x -> reportView(x, users)).toList(), c.likes(), c.collects(),
                c.comments(), c.copies(), p.fromCookRecordId(), p.city(), p.createTime(),
                logs.isEmpty() ? null : moderationView(logs.get(0), users),
                appealView(appeals.findByPosts(List.of(p.id())).get(p.id())), history(p.authorId()),
                moderation.bannedUntil(p.authorId()).filter(t -> t.isAfter(LocalDateTime.now(clock))).orElse(null),
                logs.stream().map(l -> moderationView(l, users)).toList());
    }

    @Transactional(rollbackFor = Exception.class)
    public void moderate(Long operatorId, Long postId, String action, String reason, String note) {
        ModerationAction a = ModerationAction.of(action);
        String r = reason == null ? "" : reason.trim();
        if (r.isEmpty()) throw new BizException("请选择处置原因");
        if (r.length() > MAX_REASON) r = r.substring(0, MAX_REASON);
        String nt = note == null || note.isBlank() ? null : note.trim();
        if (nt != null && nt.length() > MAX_NOTE) nt = nt.substring(0, MAX_NOTE);
        Post p = posts.findById(postId).orElseThrow(() -> new BizException("帖子不存在"));
        PostStatus before = p.status();
        p.moderate(a);
        posts.save(p);
        LocalDateTime now = LocalDateTime.now(clock).withNano(0);
        if (a == ModerationAction.BAN_7D) moderation.ban(p.authorId(), now.plusDays(BAN_DAYS), r);
        moderation.log(p.id(), operatorId, a, r, nt, now);
        appeals.markHandled(p.id(), now);
        if (a == ModerationAction.APPROVE && before != PostStatus.PUBLISHED) postAppService.onPublished(p);
    }

    private AuthorHistory history(Long authorId) {
        return new AuthorHistory(posts.countByAuthor(authorId, null), posts.countByAuthor(authorId, PostStatus.HIDDEN),
                moderation.banCount(authorId));
    }

    private static ModerationView moderationView(LogEntry l, Map<Long, UserLite> users) {
        if (l == null) return null;
        return new ModerationView(l.action().name(), l.reason(), l.note(), user(users, l.operatorId()), l.at());
    }

    private static AppealView appealView(Appeal a) {
        return a == null ? null : new AppealView(a.text(), a.createdAt());
    }

    private static List<ScoreView> scores(ModerationState m) {
        return m.scores().stream().map(s -> new ScoreView(s.label(), s.score())).toList();
    }

    private static ReportView reportView(Report r, Map<Long, UserLite> users) {
        return new ReportView(user(users, r.reporterId()), r.reason(), r.createdAt());
    }

    private static UserLite user(Map<Long, UserLite> users, Long id) {
        return users.getOrDefault(id, new UserLite(id, "厨友", null));
    }

    private static RiskLevel parseRisk(String s) {
        try {
            return RiskLevel.valueOf(s);
        } catch (IllegalArgumentException e) {
            throw new BizException("risk 只能是 HIGH / MEDIUM / LOW");
        }
    }
}
