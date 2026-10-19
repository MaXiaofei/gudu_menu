package com.gudu.xsd.modules.community.application;

import com.gudu.xsd.common.BizException;
import com.gudu.xsd.modules.community.application.dto.CommunityViews.Banner;
import com.gudu.xsd.modules.community.application.dto.CommunityViews.CursorPage;
import com.gudu.xsd.modules.community.application.dto.CommunityViews.DayProgress;
import com.gudu.xsd.modules.community.application.dto.CommunityViews.FollowResult;
import com.gudu.xsd.modules.community.application.dto.CommunityViews.MyProgress;
import com.gudu.xsd.modules.community.application.dto.CommunityViews.PostCard;
import com.gudu.xsd.modules.community.application.dto.CommunityViews.RankRow;
import com.gudu.xsd.modules.community.application.dto.CommunityViews.TopicDetail;
import com.gudu.xsd.modules.community.application.dto.CommunityViews.TopicView;
import com.gudu.xsd.modules.community.application.dto.CommunityViews.UserLite;
import com.gudu.xsd.modules.community.domain.model.ChallengeWeek;
import com.gudu.xsd.modules.community.domain.model.FeedCursor;
import com.gudu.xsd.modules.community.domain.model.FeedEntry;
import com.gudu.xsd.modules.community.domain.model.FeedPage;
import com.gudu.xsd.modules.community.domain.model.Post;
import com.gudu.xsd.modules.community.domain.model.PostCounts;
import com.gudu.xsd.modules.community.domain.model.Topic;
import com.gudu.xsd.modules.community.domain.repository.FollowInbox;
import com.gudu.xsd.modules.community.domain.repository.InteractionRepository;
import com.gudu.xsd.modules.community.domain.repository.PostRepository;
import com.gudu.xsd.modules.community.domain.repository.TopicRepository;
import com.gudu.xsd.modules.community.domain.service.FeedPager;
import com.gudu.xsd.modules.community.domain.service.HotScore;
import com.gudu.xsd.modules.community.domain.repository.TopicRepository.Checkin;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Feed 与关系用例。
 * <ul>
 *   <li>推荐：近 {@link #HOT_WINDOW_DAYS} 天公开帖按 {@link HotScore} 排序（挑战话题加权），游标冻结 asOf；</li>
 *   <li>关注：Redis 收件箱（写扩散）+ 关注的大 V 近期帖（读扩散）合并；</li>
 *   <li>话题：按发布时间 keyset 分页。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class CommunityFeedAppService {

    static final int HOT_WINDOW_DAYS = 14;
    static final int HOT_CANDIDATES = 500;
    static final double CHALLENGE_BOOST = 0.2;
    static final int BACKFILL = 20;
    static final int BANNER_USERS = 12;
    static final int RANK_TOP = 5;

    private final PostRepository posts;
    private final InteractionRepository interactions;
    private final TopicRepository topics;
    private final FollowInbox inbox;
    private final CommunityAssembler assembler;
    private final Clock clock;

    public CursorPage<PostCard> feed(Long viewerId, String tab, Long topicId, String cursor, Integer size) {
        int n = FeedPager.normalizeSize(size);
        FeedCursor c = FeedCursor.decode(cursor);
        String t = tab == null || tab.isBlank() ? "recommend" : tab;
        return switch (t) {
            case "recommend" -> recommend(viewerId, c, n);
            case "follow" -> follow(viewerId, c, n);
            case "topic" -> topic(viewerId, topicId, c, n);
            default -> throw new BizException("tab 只能是 recommend / follow / topic");
        };
    }

    private CursorPage<PostCard> recommend(Long viewerId, FeedCursor c, int n) {
        long now = nowMs();
        long asOf = c == null ? now : c.asOfEpochMs();
        List<Post> candidates = posts.recentPublic(LocalDateTime.now(clock).minusDays(HOT_WINDOW_DAYS), HOT_CANDIDATES);
        Set<Long> challengeTopics = topics.all().stream().filter(Topic::challenge).map(Topic::id)
                .collect(Collectors.toSet());
        Map<Long, PostCounts> counts = assembler.counts(candidates);
        List<FeedEntry> entries = candidates.stream().map(p -> {
            long created = epochMs(p.createTime());
            double hours = Math.max(0, (asOf - created) / 3_600_000.0);
            double boost = p.topicIds().stream().anyMatch(challengeTopics::contains) ? CHALLENGE_BOOST : 0;
            return new FeedEntry(p.id(), HotScore.of(counts.get(p.id()), hours, boost), created);
        }).toList();
        FeedPage page = FeedPager.page(entries, c, n, now);
        return toCards(page, candidates, viewerId);
    }

    private CursorPage<PostCard> follow(Long viewerId, FeedCursor c, int n) {
        if (viewerId == null) return new CursorPage<>(List.of(), null);
        List<FeedEntry> entries = new ArrayList<>(inbox.recent(viewerId, FollowInbox.CAPACITY));
        Set<Long> bigVs = interactions.bigFollowees(viewerId, CommunityPostAppService.BIG_V_FOLLOWERS);
        if (!bigVs.isEmpty()) {
            for (Post p : posts.recentByAuthors(bigVs, LocalDateTime.now(clock).minusDays(30), 200)) {
                long ms = epochMs(p.createTime());
                entries.add(new FeedEntry(p.id(), ms, ms));
            }
        }
        FeedPage page = FeedPager.page(entries, c, n, nowMs());
        List<Post> loaded = posts.findByIds(page.postIds());
        Set<Long> followees = interactions.followees(viewerId);
        List<Post> visible = loaded.stream()
                .filter(p -> p.visibleTo(viewerId, followees.contains(p.authorId())))
                .filter(p -> p.isAuthor(viewerId) || followees.contains(p.authorId()))
                .toList();
        return toCards(page, visible, viewerId);
    }

    private CursorPage<PostCard> topic(Long viewerId, Long topicId, FeedCursor c, int n) {
        if (topicId == null) throw new BizException("请指定话题");
        topics.findById(topicId).orElseThrow(() -> new BizException("话题不存在"));
        long asOf = c == null ? nowMs() : c.asOfEpochMs();
        LocalDateTime beforeTime = c == null ? null : toTime((long) c.score());
        Long beforeId = c == null ? null : c.id();
        List<Post> rows = posts.topicPage(topicId, beforeTime, beforeId, toTime(asOf), n + 1);
        boolean more = rows.size() > n;
        List<Post> page = more ? rows.subList(0, n) : rows;
        FeedCursor next = null;
        if (more) {
            Post last = page.get(page.size() - 1);
            next = new FeedCursor(epochMs(last.createTime()), last.id(), asOf);
        }
        return new CursorPage<>(assembler.cards(page, viewerId), next == null ? null : next.encode());
    }

    private CursorPage<PostCard> toCards(FeedPage page, List<Post> pool, Long viewerId) {
        List<Post> ordered = CommunityAssembler.ordered(page.postIds(), pool);
        return new CursorPage<>(assembler.cards(ordered, viewerId), page.next() == null ? null : page.next().encode());
    }

    // ============================ 横幅 ============================

    public Banner banner(Long viewerId) {
        LocalDate today = LocalDate.now(clock);
        TopicView topic = topics.all().stream().filter(t -> t.challenge() && t.activeOn(today)).findFirst()
                .map(t -> topicView(t, today)).orElse(null);
        List<UserLite> updates = List.of();
        if (viewerId != null) {
            Set<Long> followees = interactions.followees(viewerId);
            if (!followees.isEmpty()) {
                LinkedHashSet<Long> authors = posts.recentByAuthors(followees, LocalDateTime.now(clock).minusDays(3), 100)
                        .stream().map(Post::authorId).collect(Collectors.toCollection(LinkedHashSet::new));
                Map<Long, UserLite> users = assembler.users(authors);
                updates = authors.stream().map(users::get).filter(Objects::nonNull).limit(BANNER_USERS).toList();
            }
        }
        return new Banner(topic, updates);
    }

    // ============================ 关注 ============================

    @Transactional(rollbackFor = Exception.class)
    public FollowResult follow(Long followerId, Long followeeId, boolean on) {
        CommunityPostAppService.requireLogin(followerId);
        if (followeeId == null) throw new BizException("用户不存在");
        if (followerId.equals(followeeId)) throw new BizException("不能关注自己");
        if (assembler.users(List.of(followeeId)).isEmpty()) throw new BizException("用户不存在");
        if (on) {
            if (interactions.follow(followerId, followeeId)) {
                // 关注后回填对方最近的帖子，关注流立刻有内容
                for (Post p : posts.recentByAuthors(List.of(followeeId), LocalDateTime.now(clock).minusDays(30), BACKFILL)) {
                    inbox.push(List.of(followerId), p.id(), epochMs(p.createTime()));
                }
            }
        } else if (interactions.unfollow(followerId, followeeId)) {
            inbox.remove(followerId, posts.recentByAuthors(List.of(followeeId),
                    LocalDateTime.now(clock).minusDays(365), FollowInbox.CAPACITY).stream().map(Post::id).toList());
        }
        return new FollowResult(interactions.isFollowing(followerId, followeeId));
    }

    // ============================ 话题挑战 ============================

    public TopicDetail topicDetail(Long viewerId, Long topicId) {
        Topic t = topics.findById(topicId).orElseThrow(() -> new BizException("话题不存在"));
        LocalDate today = LocalDate.now(clock);
        TopicView v = topicView(t, today);
        ChallengeWeek week = ChallengeWeek.of(today);
        List<Checkin> checkins = t.challenge() ? topics.checkins(t.id(), week.monday(), week.sunday()) : List.of();
        Map<Long, Post> checkinPosts = posts.findByIds(checkins.stream().map(Checkin::postId).toList()).stream()
                .collect(Collectors.toMap(Post::id, p -> p, (a, b) -> a));

        MyProgress my = null;
        if (t.challenge()) {
            Map<LocalDate, Checkin> mine = new HashMap<>();
            if (viewerId != null) {
                checkins.stream().filter(ck -> viewerId.equals(ck.userId())).forEach(ck -> mine.put(ck.date(), ck));
            }
            List<DayProgress> days = week.days().stream().map(d -> {
                Checkin ck = mine.get(d);
                Post p = ck == null ? null : checkinPosts.get(ck.postId());
                return new DayProgress(d, ck != null, p == null ? null : p.cover());
            }).toList();
            my = new MyProgress(days, mine.size());
        }

        // 排行：本周打卡天数降序，同天数按打卡帖获赞降序
        Map<Long, PostCounts> counts = assembler.counts(new ArrayList<>(checkinPosts.values()));
        Map<Long, int[]> dayCount = new LinkedHashMap<>();
        Map<Long, Long> likeSum = new HashMap<>();
        for (Checkin ck : checkins) {
            dayCount.computeIfAbsent(ck.userId(), k -> new int[1])[0]++;
            PostCounts pc = counts.get(ck.postId());
            likeSum.merge(ck.userId(), pc == null ? 0 : pc.likes(), Long::sum);
        }
        List<Long> rankedUsers = dayCount.keySet().stream()
                .sorted(Comparator.<Long>comparingInt(u -> dayCount.get(u)[0]).reversed()
                        .thenComparing(Comparator.<Long>comparingLong(u -> likeSum.getOrDefault(u, 0L)).reversed())
                        .thenComparing(Comparator.naturalOrder()))
                .toList();
        List<Long> top = rankedUsers.stream().limit(RANK_TOP).toList();
        Map<Long, UserLite> users = assembler.users(top);
        List<RankRow> ranking = new ArrayList<>();
        for (int i = 0; i < top.size(); i++) {
            Long u = top.get(i);
            ranking.add(new RankRow(users.getOrDefault(u, new UserLite(u, "厨友", null)), dayCount.get(u)[0],
                    likeSum.getOrDefault(u, 0L), i + 1));
        }
        int idx = viewerId == null ? -1 : rankedUsers.indexOf(viewerId);
        return new TopicDetail(v.topicId(), v.name(), v.intro(), v.rule(), v.startDate(), v.endDate(), v.weekNo(),
                v.participantCount(), v.postCount(), v.coverUrls(), my, ranking, idx < 0 ? null : idx + 1);
    }

    /** 话题列表（发布页选话题）：ACTIVE = 今天进行中的（默认），ALL = 全部；按 sort 排序。 */
    public List<TopicView> topics(String status, Integer size) {
        String st = status == null || status.isBlank() ? "ACTIVE" : status;
        if (!"ACTIVE".equals(st) && !"ALL".equals(st)) throw new BizException("status 只能是 ACTIVE / ALL");
        int n = size == null || size < 1 ? 20 : Math.min(size, 50);
        LocalDate today = LocalDate.now(clock);
        return topics.all().stream().filter(t -> "ALL".equals(st) || t.activeOn(today)).limit(n)
                .map(t -> topicView(t, today)).toList();
    }

    /** 挑战话题的 startDate/endDate 返回本周周一/周日，weekNo 为第几周；普通话题返回话题本身起止。 */
    private TopicView topicView(Topic t, LocalDate today) {
        ChallengeWeek week = ChallengeWeek.of(today);
        return new TopicView(t.id(), t.name(), t.intro(), t.rule(),
                t.challenge() ? week.monday() : t.startDate(), t.challenge() ? week.sunday() : t.endDate(),
                t.challenge() ? week.weekNumberSince(t.startDate()) : null,
                topics.participantCount(t.id()), posts.countByTopic(t.id()), posts.recentCoversOfTopic(t.id(), 4));
    }

    // ============================ 时间 ============================

    private long nowMs() {
        return clock.millis();
    }

    private long epochMs(LocalDateTime t) {
        ZoneId zone = clock.getZone();
        return t.atZone(zone).toInstant().toEpochMilli();
    }

    private LocalDateTime toTime(long epochMs) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMs), clock.getZone());
    }
}
