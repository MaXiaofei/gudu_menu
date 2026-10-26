package com.gudu.xsd.modules.feedback.application;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gudu.xsd.common.BizException;
import com.gudu.xsd.common.domain.DomainException;
import com.gudu.xsd.modules.contentsecurity.application.ContentSecurityAppService;
import com.gudu.xsd.modules.contentsecurity.domain.model.ContentPurpose;
import com.gudu.xsd.modules.feedback.domain.model.Feedback;
import com.gudu.xsd.modules.feedback.domain.model.FeedbackPage;
import com.gudu.xsd.modules.feedback.domain.model.FeedbackStatus;
import com.gudu.xsd.modules.feedback.domain.model.FeedbackType;
import com.gudu.xsd.modules.feedback.domain.repository.FeedbackRepository;
import com.gudu.xsd.modules.member.Member;
import com.gudu.xsd.modules.member.MemberService;
import com.gudu.xsd.modules.notification.NotificationPayload;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FeedbackAppServiceTest {

    static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    static final LocalDate TODAY = LocalDate.of(2026, 10, 16);
    static final LocalDateTime NOW = TODAY.atTime(9, 0);

    FeedbackRepository repo;
    MemberService memberService;
    FeedbackAppService svc;
    ContentSecurityAppService contentSecurity;
    NotificationService notifications;

    @BeforeEach
    void setUp() {
        repo = mock(FeedbackRepository.class);
        memberService = mock(MemberService.class);
        contentSecurity = mock(ContentSecurityAppService.class);
        notifications = mock(NotificationService.class);
        svc = new FeedbackAppService(repo, memberService,
                Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE), contentSecurity, notifications);
    }

    private static FeedbackSubmitCommand cmd(String content) {
        return new FeedbackSubmitCommand(content, null, null, null, null, null);
    }

    private static Feedback stored(long id, long member) {
        return Feedback.rehydrate(id, member, FeedbackType.AI, "agent:m1", "内容够长了", "wx", List.of(), "mini",
                FeedbackStatus.NEW, null, null, null, LocalDateTime.of(2026, 10, 16, 8, 0));
    }

    @Test
    void 提交_保存返回id_限流按当天0点起算() {
        doAnswer(inv -> {
            inv.getArgument(0, Feedback.class).identify(8L);
            return null;
        }).when(repo).save(any());
        assertThat(svc.submit(1L, cmd("希望能导出菜谱"))).isEqualTo(8L);
        verify(repo).countByMemberSince(1L, TODAY.atStartOfDay());
    }

    @Test
    void 提交_带类型与来源引用_落到聚合() {
        svc.submit(1L, new FeedbackSubmitCommand("AI 回答的做法不对", null, null, "mini", "ai", "agent:m123"));
        ArgumentCaptor<Feedback> saved = ArgumentCaptor.forClass(Feedback.class);
        verify(repo).save(saved.capture());
        assertThat(saved.getValue().type()).isEqualTo(FeedbackType.AI);
        assertThat(saved.getValue().sourceRef()).isEqualTo("agent:m123");
        assertThat(saved.getValue().clientInfo()).isEqualTo("mini");
    }

    @Test
    void 提交_非法类型_拒绝() {
        assertThatThrownBy(() -> svc.submit(1L, new FeedbackSubmitCommand("内容够长了", null, null, null, "bug", null)))
                .isInstanceOf(DomainException.class).hasMessageContaining("类型");
        verify(repo, never()).save(any());
    }

    @Test
    void 提交_当天已10条_429() {
        when(repo.countByMemberSince(eq(1L), any())).thenReturn(10L);
        assertThatThrownBy(() -> svc.submit(1L, cmd("希望能导出菜谱")))
                .isInstanceOfSatisfying(BizException.class, e -> assertThat(e.getCode()).isEqualTo(429));
        verify(repo, never()).save(any());
    }

    @Test
    void 提交_内容安全不通过_451且不保存() {
        doThrow(BizException.contentBlocked("内容未通过内容安全检测，请修改后再试"))
                .when(contentSecurity).assertText(1L, ContentPurpose.FEEDBACK, "违规的反馈内容");
        assertThatThrownBy(() -> svc.submit(1L, cmd("违规的反馈内容")))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo(451));
        verify(repo, never()).save(any());
    }

    @Test
    void 列表_回填成员名_分页参数夹紧_类型与状态过滤() {
        when(repo.page(eq(FeedbackType.AI), eq(FeedbackStatus.NEW), anyInt(), anyInt()))
                .thenReturn(new FeedbackPage(List.of(stored(3L, 1L)), 1, 1, 100));
        Member m = new Member();
        m.setId(1L);
        m.setName("张爸爸");
        when(memberService.listByIds(any())).thenReturn(List.of(m));

        IPage<FeedbackView> p = svc.page("ai", "NEW", 0, 1000);

        verify(repo).page(FeedbackType.AI, FeedbackStatus.NEW, 1, 100);
        assertThat(p.getTotal()).isEqualTo(1);
        FeedbackView v = p.getRecords().get(0);
        assertThat(v.memberName()).isEqualTo("张爸爸");
        assertThat(v.status()).isEqualTo("NEW");
        assertThat(v.type()).isEqualTo("ai");
        assertThat(v.sourceRef()).isEqualTo("agent:m1");
    }

    @Test
    void 列表_不传筛选_空页不查成员() {
        when(repo.page(null, null, 1, 10)).thenReturn(new FeedbackPage(List.of(), 0, 1, 10));
        assertThat(svc.page(null, null, null, null).getRecords()).isEmpty();
        verify(memberService, never()).listByIds(any());
    }

    @Test
    void 改状态_存在则保存_不存在404() {
        Feedback f = stored(3L, 1L);
        when(repo.findById(3L)).thenReturn(Optional.of(f));
        svc.changeStatus(3L, "DONE");
        assertThat(f.status()).isEqualTo(FeedbackStatus.DONE);
        verify(repo).save(f);
        assertThatThrownBy(() -> svc.changeStatus(4L, "DONE")).hasMessageContaining("反馈不存在");
    }

    @Test
    void 回复_状态变REPLIED_记录回复人_给提交人发站内消息指向反馈() {
        Feedback f = stored(3L, 7L);
        when(repo.findById(3L)).thenReturn(Optional.of(f));

        svc.reply(1L, 3L, "  已修复，感谢反馈  ", false);

        assertThat(f.status()).isEqualTo(FeedbackStatus.REPLIED);
        assertThat(f.reply()).isEqualTo("已修复，感谢反馈");
        assertThat(f.repliedBy()).isEqualTo(1L);
        assertThat(f.repliedAt()).isEqualTo(NOW);
        verify(repo).save(f);
        ArgumentCaptor<NotificationPayload> msg = ArgumentCaptor.forClass(NotificationPayload.class);
        verify(notifications).send(msg.capture(), eq("in_app"));
        assertThat(msg.getValue().memberId()).isEqualTo(7L);
        assertThat(msg.getValue().type()).isEqualTo(FeedbackAppService.NOTICE_TYPE_REPLY);
        assertThat(msg.getValue().refId()).isEqualTo("3");
        assertThat(msg.getValue().content()).isEqualTo("已修复，感谢反馈");
    }

    @Test
    void 回复并标记处理_状态DONE_长回复消息截断() {
        Feedback f = stored(3L, 7L);
        when(repo.findById(3L)).thenReturn(Optional.of(f));
        svc.reply(1L, 3L, "好".repeat(100), true);
        assertThat(f.status()).isEqualTo(FeedbackStatus.DONE);
        ArgumentCaptor<NotificationPayload> msg = ArgumentCaptor.forClass(NotificationPayload.class);
        verify(notifications).send(msg.capture(), anyString());
        assertThat(msg.getValue().content()).hasSize(61).endsWith("…");
    }

    @Test
    void 回复_空内容拒绝_不发消息() {
        when(repo.findById(3L)).thenReturn(Optional.of(stored(3L, 7L)));
        assertThatThrownBy(() -> svc.reply(1L, 3L, " ", false)).isInstanceOf(DomainException.class);
        verify(repo, never()).save(any());
        verify(notifications, never()).send(any(), any());
    }

    @Test
    void 我的反馈_只查本人_详情非本人404() {
        when(repo.pageByMember(7L, 1, 10)).thenReturn(new FeedbackPage(List.of(stored(3L, 7L)), 1, 1, 10));
        IPage<MyFeedbackView> p = svc.mine(7L, null, null);
        assertThat(p.getRecords()).extracting(MyFeedbackView::id).containsExactly(3L);
        assertThat(p.getRecords().get(0).type()).isEqualTo("ai");

        when(repo.findById(3L)).thenReturn(Optional.of(stored(3L, 7L)));
        assertThat(svc.mineDetail(7L, 3L).content()).isEqualTo("内容够长了");
        assertThatThrownBy(() -> svc.mineDetail(8L, 3L))
                .isInstanceOfSatisfying(BizException.class, e -> assertThat(e.getCode()).isEqualTo(404));
    }
}
