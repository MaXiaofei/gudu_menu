package com.gudu.xsd.modules.community.domain;

import com.gudu.xsd.common.domain.DomainException;
import com.gudu.xsd.modules.community.domain.model.ChallengeWeek;
import com.gudu.xsd.modules.community.domain.model.Comment;
import com.gudu.xsd.modules.community.domain.model.FeedCursor;
import com.gudu.xsd.modules.community.domain.model.FeedEntry;
import com.gudu.xsd.modules.community.domain.model.FeedPage;
import com.gudu.xsd.modules.community.domain.model.ModerationAction;
import com.gudu.xsd.modules.community.domain.model.ModerationVerdict;
import com.gudu.xsd.modules.community.domain.model.Post;
import com.gudu.xsd.modules.community.domain.model.PostCounts;
import com.gudu.xsd.modules.community.domain.model.PostStatus;
import com.gudu.xsd.modules.community.domain.model.PostVisibility;
import com.gudu.xsd.modules.community.domain.model.RiskLevel;
import com.gudu.xsd.modules.community.domain.service.FeedPager;
import com.gudu.xsd.modules.community.domain.service.HotScore;
import com.gudu.xsd.modules.community.domain.service.ModerationSummary;
import com.gudu.xsd.modules.community.domain.service.ReportPolicy;
import com.gudu.xsd.modules.community.domain.service.RulePostModerator;
import com.gudu.xsd.modules.community.domain.service.TemplatePostTextWriter;
import com.gudu.xsd.modules.community.domain.service.PostTextWriter;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommunityDomainTest {

    static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 16, 18, 40);
    static final String IMG = "/gudu/uploads/2026/10/a.jpg";

    static Post publish(Long author, List<String> images) {
        return Post.publish(author, "第一次做清蒸鲈鱼", "火候刚好", images, 1L, List.of(), PostVisibility.PUBLIC,
                null, "上海", 0.75, NOW);
    }

    // ---------------- 发布与预审 ----------------

    @Test
    void 发布_校验标题与图片() {
        assertThatThrownBy(() -> Post.publish(1L, " ", "", List.of(IMG), null, List.of(), null, null, null, null, NOW))
                .isInstanceOf(DomainException.class).hasMessageContaining("标题");
        assertThatThrownBy(() -> publish(1L, List.of())).hasMessageContaining("至少放 1 张");
        assertThatThrownBy(() -> publish(1L, List.of("javascript:alert(1)"))).hasMessageContaining("图片地址不合法");
        List<String> ten = new ArrayList<>();
        for (int i = 0; i < 10; i++) ten.add(IMG);
        assertThatThrownBy(() -> publish(1L, ten)).hasMessageContaining("最多 9 张");
    }

    @Test
    void 发布_本站菜谱图可用_站外图片被拦截并判高风险() {
        Post p = publish(1L, List.of("https://staging.imxf.cloud/images/dish/original/a.jpg",
                "https://evil.example.com/x.png"));
        assertThat(p.images()).containsExactly("https://staging.imxf.cloud/images/dish/original/a.jpg");
        assertThat(p.blockedImages()).containsExactly("https://evil.example.com/x.png");
        p.applyVerdict(ModerationVerdict.low());
        assertThat(p.risk()).isEqualTo(RiskLevel.HIGH);
        assertThat(p.status()).isEqualTo(PostStatus.REVIEWING);
        assertThat(p.riskReasons().get(0)).contains("外链图片");
        assertThat(p.moderation().scores().get(0).label()).isEqualTo("外链图片");
    }

    @Test
    void 只有站外图片时拒绝发布() {
        assertThatThrownBy(() -> publish(1L, List.of("https://evil.example.com/x.png")))
                .hasMessageContaining("本站上传");
    }

    @Test
    void 高风险帖子对他人不可见_作者可见() {
        Post p = publish(1L, List.of(IMG));
        p.applyVerdict(new RulePostModerator().review("加微信 abc12345 私聊", "", List.of()));
        assertThat(p.status()).isEqualTo(PostStatus.REVIEWING);
        assertThat(p.visibleTo(2L, true)).isFalse();
        assertThat(p.visibleTo(1L, false)).isTrue();
        p.moderate(ModerationAction.APPROVE);
        assertThat(p.visibleTo(2L, false)).isTrue();
        assertThat(p.reviewed()).isTrue();
    }

    @Test
    void 仅关注可见的帖子要求已关注作者() {
        Post p = Post.publish(1L, "标题", "", List.of(IMG), null, List.of(), PostVisibility.FOLLOWERS, null, null,
                null, NOW);
        p.applyVerdict(ModerationVerdict.low());
        assertThat(p.visibleTo(2L, false)).isFalse();
        assertThat(p.visibleTo(2L, true)).isTrue();
    }

    @Test
    void 规则预审分级() {
        RulePostModerator m = new RulePostModerator();
        assertThat(m.review("清蒸鲈鱼", "火候很重要", List.of()).risk()).isEqualTo(RiskLevel.LOW);
        assertThat(m.review("清蒸鲈鱼", "有优惠券", List.of()).risk()).isEqualTo(RiskLevel.MEDIUM);
        assertThat(m.review("清蒸鲈鱼", "代购 优惠券", List.of()).risk()).isEqualTo(RiskLevel.HIGH);
        assertThat(m.review("清蒸鲈鱼", "电话 13812345678", List.of()).risk()).isEqualTo(RiskLevel.HIGH);
        assertThat(m.review("x", "详见 https://t.cn/abc", List.of()).reasons()).contains("包含外部链接");
        assertThatThrownBy(() -> m.assertPublishable("赌博", "")).isInstanceOf(DomainException.class);
        assertThat(m.review("a", "代购", List.of()).scores()).extracting("label")
                .containsExactly("引流广告", "联系方式", "违禁内容");
    }

    @Test
    void 举报达阈值自动隐藏并回到审核队列() {
        assertThat(ReportPolicy.shouldAutoHide(2)).isFalse();
        assertThat(ReportPolicy.shouldAutoHide(3)).isTrue();
        Post p = publish(1L, List.of(IMG));
        p.applyVerdict(ModerationVerdict.low());
        assertThat(p.reviewed()).isTrue();
        assertThat(p.hideByReports()).isTrue();
        assertThat(p.status()).isEqualTo(PostStatus.HIDDEN);
        assertThat(p.reviewed()).isFalse();
        assertThat(p.hideByReports()).isFalse();
    }

    @Test
    void 申诉只限被隐藏帖子的作者() {
        Post p = publish(1L, List.of(IMG));
        p.applyVerdict(ModerationVerdict.low());
        assertThatThrownBy(() -> p.checkAppealable(1L)).hasMessageContaining("被隐藏");
        p.moderate(ModerationAction.HIDE);
        assertThatThrownBy(() -> p.checkAppealable(2L)).hasMessageContaining("自己的帖子");
        p.checkAppealable(1L);
    }

    @Test
    void 删除只限作者() {
        Post p = publish(1L, List.of(IMG));
        assertThatThrownBy(() -> p.deleteBy(2L)).isInstanceOf(DomainException.class);
        p.deleteBy(1L);
        assertThat(p.visibleTo(1L, true)).isFalse();
    }

    @Test
    void 封面宽高比夹在合理范围() {
        Post p = Post.publish(1L, "t", "", List.of(IMG), null, List.of(), null, null, null, 9.0, NOW);
        assertThat(p.coverRatio()).isEqualTo(Post.MAX_RATIO);
    }

    @Test
    void 评论最多两级() {
        Comment top = Comment.rehydrate(10L, 5L, 2L, null, "好吃", NOW);
        Comment reply = Comment.write(5L, 1L, "谢谢", top, NOW);
        assertThat(reply.parentId()).isEqualTo(10L);
        Comment nested = Comment.rehydrate(11L, 5L, 1L, 10L, "谢谢", NOW);
        assertThatThrownBy(() -> Comment.write(5L, 3L, "再回", nested, NOW)).hasMessageContaining("顶层");
        Comment other = Comment.rehydrate(12L, 6L, 1L, null, "x", NOW);
        assertThatThrownBy(() -> Comment.write(5L, 3L, "x", other, NOW)).hasMessageContaining("不属于");
    }

    // ---------------- 挑战周期 ----------------

    @Test
    void 挑战周期从周一开始() {
        ChallengeWeek w = ChallengeWeek.of(LocalDate.of(2026, 10, 16)); // 周五
        assertThat(w.monday()).isEqualTo(LocalDate.of(2026, 10, 12));
        assertThat(w.monday().getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(w.sunday()).isEqualTo(LocalDate.of(2026, 10, 18));
        assertThat(w.days()).hasSize(7);
        assertThat(ChallengeWeek.of(LocalDate.of(2026, 10, 18)).monday()).isEqualTo(LocalDate.of(2026, 10, 12));
        assertThat(ChallengeWeek.of(LocalDate.of(2026, 10, 19)).monday()).isEqualTo(LocalDate.of(2026, 10, 19));
        assertThat(w.weekNumberSince(LocalDate.of(2026, 9, 28))).isEqualTo(3);
    }

    // ---------------- Feed ----------------

    @Test
    void 游标翻页不重复不遗漏_翻页期间的新帖不插入本轮() {
        long t0 = 1_000_000L;
        List<FeedEntry> entries = new ArrayList<>();
        for (int i = 1; i <= 45; i++) entries.add(new FeedEntry(i, i % 7, t0 - i)); // 大量同分
        FeedPage p1 = FeedPager.page(entries, null, 20, t0);
        // 翻页期间新发 3 帖（分数更高、时间晚于 asOf）
        List<FeedEntry> more = new ArrayList<>(entries);
        for (int i = 100; i < 103; i++) more.add(new FeedEntry(i, 99, t0 + 10));
        FeedPage p2 = FeedPager.page(more, FeedCursor.decode(p1.next().encode()), 20, t0 + 20);
        FeedPage p3 = FeedPager.page(more, p2.next(), 20, t0 + 30);
        List<Long> all = new ArrayList<>();
        all.addAll(p1.postIds());
        all.addAll(p2.postIds());
        all.addAll(p3.postIds());
        Set<Long> unique = new HashSet<>(all);
        assertThat(all).hasSize(45);
        assertThat(unique).hasSize(45).doesNotContain(100L, 101L, 102L);
        assertThat(p3.next()).isNull();
    }

    @Test
    void 游标非法时报错() {
        assertThatThrownBy(() -> FeedCursor.decode("@@@")).isInstanceOf(DomainException.class);
        assertThat(FeedCursor.decode(null)).isNull();
    }

    @Test
    void 热度分_照着做权重最高_随时间衰减() {
        double likes = HotScore.of(new PostCounts(2, 0, 0, 0), 1, 0);
        double copies = HotScore.of(new PostCounts(0, 0, 0, 1), 1, 0);
        assertThat(copies).isGreaterThan(likes);
        assertThat(HotScore.of(new PostCounts(10, 0, 0, 0), 48, 0))
                .isLessThan(HotScore.of(new PostCounts(10, 0, 0, 0), 2, 0));
        assertThat(HotScore.of(PostCounts.ZERO, 1, 0.2)).isGreaterThan(HotScore.of(PostCounts.ZERO, 1, 0));
    }

    // ---------------- 文案 ----------------

    @Test
    void 模板文案三种风格且不含家庭成员() {
        TemplatePostTextWriter w = new TemplatePostTextWriter();
        PostTextWriter.Material m = new PostTextWriter.Material("滑蛋虾仁", 10, "今天 18:40", "简单");
        assertThat(w.write(m, "default")).contains("滑蛋虾仁").contains("10 分钟");
        assertThat(w.write(m, "short")).contains("10 分钟搞定");
        assertThat(w.write(m, "casual")).contains("今天 18:40");
        assertThat(w.write(null, null)).contains("这道家常菜");
    }

    @Test
    void 预审摘要() {
        Post p = publish(1L, List.of(IMG));
        p.applyVerdict(new RulePostModerator().review("加微信 abcdefg1", "", List.of()));
        assertThat(ModerationSummary.of(p.moderation(), 2)).contains("高风险").contains("2 人举报").contains("建议隐藏");
    }
}
