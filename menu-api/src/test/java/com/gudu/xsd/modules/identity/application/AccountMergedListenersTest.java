package com.gudu.xsd.modules.identity.application;

import com.gudu.xsd.modules.community.application.CommunityAccountMergedListener;
import com.gudu.xsd.modules.community.domain.repository.AppealRepository;
import com.gudu.xsd.modules.community.domain.repository.CommentRepository;
import com.gudu.xsd.modules.community.domain.repository.InteractionRepository;
import com.gudu.xsd.modules.community.domain.repository.PostRepository;
import com.gudu.xsd.modules.community.infrastructure.MybatisInteractionRepository;
import com.gudu.xsd.modules.community.infrastructure.mapper.CommunityRelationMapper;
import com.gudu.xsd.modules.family.domain.model.Family;
import com.gudu.xsd.modules.family.domain.model.FamilyName;
import com.gudu.xsd.modules.family.domain.model.FamilyRole;
import com.gudu.xsd.modules.family.domain.model.Invitation;
import com.gudu.xsd.modules.family.domain.model.Membership;
import com.gudu.xsd.modules.feedback.application.FeedbackAccountMergedListener;
import com.gudu.xsd.modules.feedback.domain.repository.FeedbackRepository;
import com.gudu.xsd.modules.identity.domain.event.AccountMerged;
import com.gudu.xsd.modules.menu.together.TogetherAccountMergedListener;
import com.gudu.xsd.modules.menu.together.mapper.MenuActivityMapper;
import com.gudu.xsd.modules.menu.together.mapper.MenuInviteMapper;
import com.gudu.xsd.modules.menu.together.mapper.MenuJoinMapper;
import com.gudu.xsd.modules.menu.together.MenuJoin;
import com.gudu.xsd.modules.vision.application.VisionAccountMergedListener;
import com.gudu.xsd.modules.vision.domain.repository.VisionJobRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 账号合并的各上下文监听（打回项 2，对照 design.md「账号合并 · 数据归属表」）：按账号归属的数据由 W 改指 P。
 * W=2（微信账号）、P=1（手机号账号）。
 */
class AccountMergedListenersTest {

    static final long W = 2L;
    static final long P = 1L;
    static final AccountMerged EVENT = new AccountMerged(W, P);

    @Test
    void 意见反馈_改指P() {
        FeedbackRepository repo = mock(FeedbackRepository.class);
        new FeedbackAccountMergedListener(repo).on(EVENT);
        verify(repo).reassignAccount(W, P);
    }

    @Test
    void 拍照识别任务_改指P() {
        VisionJobRepository repo = mock(VisionJobRepository.class);
        new VisionAccountMergedListener(repo).on(EVENT);
        verify(repo).reassignAccount(W, P);
    }

    @Test
    void 厨房圈_帖子评论作者_互动关系_申诉_全部改指P() {
        PostRepository posts = mock(PostRepository.class);
        CommentRepository comments = mock(CommentRepository.class);
        InteractionRepository interactions = mock(InteractionRepository.class);
        AppealRepository appeals = mock(AppealRepository.class);
        new CommunityAccountMergedListener(posts, comments, interactions, appeals).on(EVENT);
        verify(posts).reassignAccount(W, P);
        verify(comments).reassignAccount(W, P);
        verify(interactions).reassignAccount(W, P);
        verify(appeals).reassignAccount(W, P);
    }

    @Test
    void 厨房圈关系表_唯一键冲突以P为准_先复制后删除_封禁取较晚() {
        CommunityRelationMapper mapper = mock(CommunityRelationMapper.class);
        LocalDateTime until = LocalDateTime.of(2026, 12, 1, 0, 0);
        when(mapper.bannedUntil(W)).thenReturn(until);
        when(mapper.banReason(W)).thenReturn("广告");
        new MybatisInteractionRepository(mapper).reassignAccount(W, P);
        InOrder o = inOrder(mapper);
        o.verify(mapper).copyReactions(W, P);
        o.verify(mapper).deleteReactionsOf(W);
        o.verify(mapper).copyFollowing(W, P);
        o.verify(mapper).copyFollowers(W, P);
        o.verify(mapper).deleteFollowsOf(W);
        o.verify(mapper).copyCopies(W, P);
        o.verify(mapper).deleteCopiesOf(W);
        o.verify(mapper).copyReports(W, P);
        o.verify(mapper).deleteReportsOf(W);
        o.verify(mapper).copyCheckins(W, P);
        o.verify(mapper).deleteCheckinsOf(W);
        o.verify(mapper).upsertBan(P, until, "广告");
        o.verify(mapper).deleteBan(W);
    }

    @Test
    void 厨房圈关系表_W未被封禁_不写封禁() {
        CommunityRelationMapper mapper = mock(CommunityRelationMapper.class);
        new MybatisInteractionRepository(mapper).reassignAccount(W, P);
        verify(mapper, never()).upsertBan(any(), any(), any());
    }

    @Test
    void 聚餐_加入记录_邀请人_点菜留痕_改指P_同一食集都加入过则保留P的() {
        MenuJoinMapper joins = mock(MenuJoinMapper.class);
        MenuInviteMapper invites = mock(MenuInviteMapper.class);
        MenuActivityMapper acts = mock(MenuActivityMapper.class);
        MenuJoin pJoin = new MenuJoin();
        pJoin.setMenuId(10L);
        when(joins.selectList(any())).thenReturn(List.of(pJoin));
        new TogetherAccountMergedListener(joins, invites, acts).on(EVENT);
        InOrder o = inOrder(joins);
        o.verify(joins).delete(any());          // 先删 W 在 P 也加入过的食集里的记录
        o.verify(joins).update(isNull(), any()); // 再把 W 的其余记录改指 P
        verify(invites).update(isNull(), any());
        verify(acts).update(isNull(), any());
    }

    @Test
    void 家庭_W生成的邀请改由P署名() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 26, 12, 0);
        Family f = Family.rehydrate(10L, new FamilyName("家"), null, List.of(
                new Membership(W, FamilyRole.OWNER, now), new Membership(3L, FamilyRole.MEMBER, now)),
                List.of(), now, null, 0);
        Invitation inv = f.invite(W, now, null, 0);
        f.reassignAccount(W, P);
        assertThat(f.invitation(inv.code()).orElseThrow().createdBy()).isEqualTo(P);
        assertThat(f.roleOf(P)).contains(FamilyRole.OWNER);
    }
}
