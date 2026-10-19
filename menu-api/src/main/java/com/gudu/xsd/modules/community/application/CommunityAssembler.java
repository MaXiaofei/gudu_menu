package com.gudu.xsd.modules.community.application;

import com.gudu.xsd.modules.community.application.dto.CommunityViews.Coverage;
import com.gudu.xsd.modules.community.application.dto.CommunityViews.DishCard;
import com.gudu.xsd.modules.community.application.dto.CommunityViews.PostCard;
import com.gudu.xsd.modules.community.application.dto.CommunityViews.UserLite;
import com.gudu.xsd.modules.community.domain.model.CounterDelta;
import com.gudu.xsd.modules.community.domain.model.Post;
import com.gudu.xsd.modules.community.domain.model.PostCounts;
import com.gudu.xsd.modules.community.domain.model.ReactionType;
import com.gudu.xsd.modules.community.domain.model.Topic;
import com.gudu.xsd.modules.community.domain.repository.InteractionRepository;
import com.gudu.xsd.modules.community.domain.repository.PostCounterStore;
import com.gudu.xsd.modules.community.domain.repository.TopicRepository;
import com.gudu.xsd.modules.dish.Dish;
import com.gudu.xsd.modules.dish.DishService;
import com.gudu.xsd.modules.dish.application.DishCoverageAppService;
import com.gudu.xsd.modules.member.Member;
import com.gudu.xsd.modules.member.MemberService;
import com.gudu.xsd.modules.pantry.application.dto.CoverageView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 出参组装：成员 → UserLite、菜谱 → DishCard（带覆盖率）、帖子 → PostCard（计数 = 库值 + 未回刷增量）。
 * 跨上下文只经对方应用层服务（MemberService / DishService / DishCoverageAppService）。
 */
@Component
@RequiredArgsConstructor
public class CommunityAssembler {

    private final MemberService memberService;
    private final DishService dishService;
    private final DishCoverageAppService coverageAppService;
    private final PostCounterStore counterStore;
    private final InteractionRepository interactions;
    private final TopicRepository topics;

    public Map<Long, UserLite> users(Collection<Long> ids) {
        List<Long> list = clean(ids);
        if (list.isEmpty()) return Map.of();
        return memberService.listByIds(list).stream().collect(Collectors.toMap(Member::getId,
                m -> new UserLite(m.getId(), m.getName() == null ? "厨友" : m.getName(), m.getAvatarUrl()),
                (a, b) -> a));
    }

    public UserLite user(Long id) {
        UserLite u = users(id == null ? List.of() : List.of(id)).get(id);
        return u != null ? u : new UserLite(id, "厨友", null);
    }

    public Map<Long, DishCard> dishCards(Collection<Long> dishIds) {
        List<Long> ids = clean(dishIds);
        if (ids.isEmpty()) return Map.of();
        List<Dish> dishes = dishService.listByIds(ids);
        if (dishes.isEmpty()) return Map.of();
        dishService.decorateCards(dishes);
        Map<Long, CoverageView> cov = coverageAppService.coverageOf(dishes.stream().map(Dish::getId).toList());
        Map<Long, DishCard> out = new LinkedHashMap<>();
        for (Dish d : dishes) {
            CoverageView c = cov.get(d.getId());
            Coverage coverage = c == null ? new Coverage(0, 0, List.of())
                    : new Coverage(c.enough(), c.total(), c.missing().stream().map(CoverageView.Missing::name).toList());
            out.put(d.getId(), new DishCard(d.getId(), d.getName(), d.getCoverUrl(), minutes(d), difficulty(d.getDifficulty()),
                    coverage, d.getTagNames() == null ? List.of() : d.getTagNames()));
        }
        return out;
    }

    /** 计数展示值 = 库值 + 未回刷增量。 */
    public Map<Long, PostCounts> counts(List<Post> posts) {
        Map<Long, CounterDelta> pending = counterStore.pending(posts.stream().map(Post::id).toList());
        Map<Long, PostCounts> out = new HashMap<>();
        for (Post p : posts) out.put(p.id(), p.counts().plus(pending.get(p.id())));
        return out;
    }

    public PostCounts counts(Post p) {
        return counts(List.of(p)).get(p.id());
    }

    /** 按入参顺序组装卡片。 */
    public List<PostCard> cards(List<Post> posts, Long viewerId) {
        if (posts.isEmpty()) return List.of();
        Map<Long, UserLite> authors = users(posts.stream().map(Post::authorId).toList());
        Map<Long, PostCounts> counts = counts(posts);
        List<Long> ids = posts.stream().map(Post::id).toList();
        Set<Long> liked = interactions.reactedPostIds(viewerId, ids, ReactionType.LIKE);
        Map<Long, String> topicNames = topicNames(posts.stream().flatMap(p -> p.topicIds().stream()).toList());
        return posts.stream().map(p -> {
            PostCounts c = counts.get(p.id());
            return new PostCard(p.id(), authors.getOrDefault(p.authorId(), new UserLite(p.authorId(), "厨友", null)),
                    p.title(), p.cover(), p.coverRatio(), p.images().size(), c.likes(), liked.contains(p.id()),
                    c.copies(), p.topicIds().stream().map(topicNames::get).filter(Objects::nonNull).toList());
        }).toList();
    }

    public Map<Long, String> topicNames(Collection<Long> topicIds) {
        return topics.findByIds(topicIds).stream().collect(Collectors.toMap(Topic::id, Topic::name, (a, b) -> a));
    }

    /** 按给定 id 顺序重排（缺失的跳过）。 */
    public static List<Post> ordered(List<Long> ids, List<Post> posts) {
        Map<Long, Post> byId = posts.stream().collect(Collectors.toMap(Post::id, Function.identity(), (a, b) -> a));
        return ids.stream().map(byId::get).filter(Objects::nonNull).toList();
    }

    static Integer minutes(Dish d) {
        int m = (d.getPrepTime() == null ? 0 : d.getPrepTime()) + (d.getCookTime() == null ? 0 : d.getCookTime());
        return m > 0 ? m : null;
    }

    static String difficulty(Integer level) {
        if (level == null) return null;
        if (level <= 2) return "简单";
        return level == 3 ? "中等" : "较难";
    }

    private static List<Long> clean(Collection<Long> ids) {
        return ids == null ? List.of() : ids.stream().filter(Objects::nonNull).distinct().toList();
    }
}
