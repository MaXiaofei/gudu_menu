package com.gudu.xsd.modules.community.application;

import com.gudu.xsd.common.BizException;
import com.gudu.xsd.modules.community.application.CommunityPostAppService.PublishCommand;
import com.gudu.xsd.modules.community.domain.model.ModerationVerdict;
import com.gudu.xsd.modules.community.domain.model.Post;
import com.gudu.xsd.modules.community.domain.model.PostCounter;
import com.gudu.xsd.modules.community.domain.model.PostCounts;
import com.gudu.xsd.modules.community.domain.model.PostStatus;
import com.gudu.xsd.modules.community.domain.model.PostVisibility;
import com.gudu.xsd.modules.community.domain.model.ReactionType;
import com.gudu.xsd.modules.community.domain.model.Topic;
import com.gudu.xsd.modules.community.domain.repository.AppealRepository;
import com.gudu.xsd.modules.community.domain.repository.CommentRepository;
import com.gudu.xsd.modules.community.domain.repository.FollowInbox;
import com.gudu.xsd.modules.community.domain.repository.InteractionRepository;
import com.gudu.xsd.modules.community.domain.repository.ModerationRepository;
import com.gudu.xsd.modules.community.domain.repository.PostCounterStore;
import com.gudu.xsd.modules.community.domain.repository.PostRepository;
import com.gudu.xsd.modules.community.domain.repository.TopicRepository;
import com.gudu.xsd.modules.community.domain.service.RulePostModerator;
import com.gudu.xsd.modules.community.domain.service.TemplatePostTextWriter;
import com.gudu.xsd.modules.cookbook.application.CookingRecordAppService;
import com.gudu.xsd.modules.cookbook.domain.model.CookedRecordRef;
import com.gudu.xsd.modules.dish.Dish;
import com.gudu.xsd.modules.dish.DishService;
import com.gudu.xsd.modules.notification.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommunityPostAppServiceTest {

    static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    static final Clock CLOCK = Clock.fixed(LocalDateTime.of(2026, 10, 16, 18, 45).atZone(ZONE).toInstant(), ZONE);
    static final String IMG = "/gudu/uploads/2026/10/a.jpg";

    PostRepository posts;
    InteractionRepository interactions;
    TopicRepository topics;
    ModerationRepository moderation;
    AppealRepository appeals;
    FollowInbox inbox;
    PostCounterStore counters;
    CommunityAssembler assembler;
    DishService dishService;
    CookingRecordAppService cookingRecords;
    CommunityPostAppService svc;

    @BeforeEach
    void setUp() {
        posts = mock(PostRepository.class);
        interactions = mock(InteractionRepository.class);
        topics = mock(TopicRepository.class);
        moderation = mock(ModerationRepository.class);
        appeals = mock(AppealRepository.class);
        inbox = mock(FollowInbox.class);
        counters = mock(PostCounterStore.class);
        assembler = mock(CommunityAssembler.class);
        dishService = mock(DishService.class);
        cookingRecords = mock(CookingRecordAppService.class);
        svc = new CommunityPostAppService(posts, mock(CommentRepository.class), interactions, topics, moderation,
                appeals, inbox, counters, new RulePostModerator(), new TemplatePostTextWriter(), assembler, dishService,
                cookingRecords, mock(NotificationService.class), CLOCK);
        when(moderation.bannedUntil(anyLong())).thenReturn(Optional.empty());
        when(assembler.counts(any(Post.class))).thenReturn(PostCounts.ZERO);
        doAnswer(inv -> {
            Post p = inv.getArgument(0);
            if (p.id() == null) p.identify(100L);
            return null;
        }).when(posts).save(any(Post.class));
    }

    static Post published(Long id, Long author) {
        Post p = Post.publish(author, "清蒸鲈鱼", "", List.of(IMG), 7L, List.of(), PostVisibility.PUBLIC, null, null,
                null, LocalDateTime.of(2026, 10, 16, 12, 0));
        p.applyVerdict(ModerationVerdict.low());
        p.identify(id);
        return p;
    }

    @Test
    void 点赞幂等_重复提交不重复计数() {
        when(posts.findById(1L)).thenReturn(Optional.of(published(1L, 9L)));
        when(interactions.addReaction(1L, 2L, ReactionType.LIKE)).thenReturn(true, false);
        when(interactions.reactedPostIds(eq(2L), anyCollection(), any())).thenReturn(Set.of(1L));
        svc.react(2L, 1L, "LIKE", true);
        svc.react(2L, 1L, "LIKE", true);
        verify(counters, org.mockito.Mockito.times(1)).incr(1L, PostCounter.LIKE, 1);
    }

    @Test
    void 举报第三个不同用户时自动隐藏() {
        Post p = published(1L, 9L);
        when(posts.findById(1L)).thenReturn(Optional.of(p));
        when(interactions.addReport(eq(1L), anyLong(), any())).thenReturn(true);
        when(interactions.reportCount(1L)).thenReturn(2L, 3L);
        svc.report(2L, 1L, "广告");
        assertThat(p.status()).isEqualTo(PostStatus.PUBLISHED);
        svc.report(3L, 1L, "广告");
        assertThat(p.status()).isEqualTo(PostStatus.HIDDEN);
        verify(posts).save(p);
    }

    @Test
    void 同一用户重复举报不计数() {
        Post p = published(1L, 9L);
        when(posts.findById(1L)).thenReturn(Optional.of(p));
        when(interactions.addReport(1L, 2L, "广告")).thenReturn(false);
        svc.report(2L, 1L, "广告");
        verify(interactions, never()).reportCount(anyLong());
    }

    @Test
    void 高风险帖子他人打开详情报不存在() {
        Post p = Post.publish(9L, "加微信 abcdef12", "", List.of(IMG), null, List.of(), null, null, null, null,
                LocalDateTime.of(2026, 10, 16, 12, 0));
        p.applyVerdict(new RulePostModerator().review(p.title(), p.text(), p.images()));
        p.identify(1L);
        when(posts.findById(1L)).thenReturn(Optional.of(p));
        assertThatThrownBy(() -> svc.visiblePost(2L, 1L)).isInstanceOf(BizException.class);
        assertThat(svc.visiblePost(9L, 1L)).isSameAs(p);
    }

    @Test
    void 从食记分享只带菜和图片_不带成员名() {
        when(cookingRecords.refOf(55L)).thenReturn(Optional.of(
                new CookedRecordRef(55L, 2L, 7L, LocalDateTime.of(2026, 10, 16, 18, 40))));
        Dish dish = new Dish();
        dish.setId(7L);
        dish.setName("滑蛋虾仁");
        dish.setCoverUrl("https://staging.imxf.cloud/images/dish/original/huadan.jpg");
        when(dishService.getById(7L)).thenReturn(dish);
        var r = svc.publish(2L, new PublishCommand("10 分钟的滑蛋虾仁", "嫩到宝宝抢着吃", null, null, List.of(),
                null, 55L, null, null, null));
        ArgumentCaptor<Post> cap = ArgumentCaptor.forClass(Post.class);
        verify(posts).save(cap.capture());
        Post saved = cap.getValue();
        assertThat(r.status()).isEqualTo("PUBLISHED");
        assertThat(saved.dishId()).isEqualTo(7L);
        assertThat(saved.images()).containsExactly(dish.getCoverUrl());
        assertThat(saved.text()).isEqualTo("嫩到宝宝抢着吃");
        assertThat(saved.title() + saved.text()).doesNotContain("朵朵", "妈妈", "林林");
        verify(inbox).push(anyCollection(), eq(100L), anyLong());
    }

    @Test
    void 按食记id分享_带入同一顿饭的多道菜图() {
        when(cookingRecords.sameMealRefs(60L)).thenReturn(List.of(
                new CookedRecordRef(60L, 2L, 7L, LocalDateTime.of(2026, 10, 16, 18, 40)),
                new CookedRecordRef(61L, 2L, 8L, LocalDateTime.of(2026, 10, 16, 18, 40))));
        Dish a = new Dish();
        a.setCoverUrl("https://staging.imxf.cloud/images/dish/original/a.jpg");
        Dish b = new Dish();
        b.setCoverUrl("https://staging.imxf.cloud/images/dish/original/b.jpg");
        when(dishService.getById(7L)).thenReturn(a);
        when(dishService.getById(8L)).thenReturn(b);
        svc.publish(2L, new PublishCommand("今晚两道菜", "", null, null, List.of(), null, null, 60L, null, null));
        ArgumentCaptor<Post> cap = ArgumentCaptor.forClass(Post.class);
        verify(posts).save(cap.capture());
        assertThat(cap.getValue().images()).containsExactly(a.getCoverUrl(), b.getCoverUrl());
        assertThat(cap.getValue().dishId()).isEqualTo(7L);
        assertThat(cap.getValue().fromCookRecordId()).isEqualTo(60L);
    }

    @Test
    void 不能分享别人的食记() {
        when(cookingRecords.refOf(55L)).thenReturn(Optional.of(new CookedRecordRef(55L, 3L, 7L, null)));
        assertThatThrownBy(() -> svc.publish(2L, new PublishCommand("t", "", List.of(IMG), null, List.of(), null, 55L,
                null, null, null))).hasMessageContaining("自己的食记");
    }

    @Test
    void 发布带挑战话题_当天打卡() {
        Topic challenge = new Topic(1L, "一周不重样", "", "", true, LocalDate.of(2026, 9, 28), null, 1);
        when(topics.findByIds(anyCollection())).thenReturn(List.of(challenge));
        svc.publish(2L, new PublishCommand("周五打卡", "", List.of(IMG), null, List.of(1L), null, null, null, null, null));
        verify(topics).checkin(1L, 2L, LocalDate.of(2026, 10, 16), 100L);
    }

    @Test
    void 高风险帖子不写扩散不打卡() {
        when(topics.findByIds(anyCollection())).thenReturn(List.of());
        var r = svc.publish(2L, new PublishCommand("加我v abcdefg", "", List.of(IMG), null, List.of(), null, null,
                null, null, null));
        assertThat(r.status()).isEqualTo("REVIEWING");
        verify(inbox, never()).push(anyCollection(), anyLong(), anyLong());
    }

    @Test
    void 被封禁时不能发帖() {
        when(moderation.bannedUntil(2L)).thenReturn(Optional.of(LocalDateTime.of(2026, 10, 20, 0, 0)));
        assertThatThrownBy(() -> svc.publish(2L, new PublishCommand("t", "", List.of(IMG), null, List.of(), null, null,
                null, null, null))).hasMessageContaining("暂时不能发布");
    }

    @Test
    void 申诉_每帖一次() {
        Post p = published(1L, 2L);
        p.moderate(com.gudu.xsd.modules.community.domain.model.ModerationAction.HIDE);
        when(posts.findById(1L)).thenReturn(Optional.of(p));
        when(appeals.add(eq(1L), eq(2L), any(), any())).thenReturn(true, false);
        svc.appeal(2L, 1L, "这是我自己拍的照片");
        assertThatThrownBy(() -> svc.appeal(2L, 1L, "再申诉")).hasMessageContaining("已经申诉过");
    }

    @Test
    void 照着做幂等_已复制过返回原菜() {
        when(posts.findById(1L)).thenReturn(Optional.of(published(1L, 9L)));
        when(interactions.copiedDishId(1L, 2L)).thenReturn(Optional.of(88L));
        when(dishService.getById(88L)).thenReturn(new Dish());
        assertThat(svc.copyRecipe(2L, 1L).dishId()).isEqualTo(88L);
        verify(counters, never()).incr(anyLong(), any(), anyLong());
        verify(dishService, never()).saveFull(any());
    }

    @Test
    void ai帮写使用食记时间() {
        when(cookingRecords.refOf(55L)).thenReturn(Optional.of(
                new CookedRecordRef(55L, 2L, 7L, LocalDateTime.of(2026, 10, 16, 18, 40))));
        Dish dish = new Dish();
        dish.setName("滑蛋虾仁");
        dish.setCookTime(10);
        when(dishService.getById(7L)).thenReturn(dish);
        assertThat(svc.aiText(2L, null, 55L, "casual").text()).contains("今天 18:40").contains("滑蛋虾仁");
    }
}
