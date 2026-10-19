package com.gudu.xsd.modules.feedback.application;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gudu.xsd.common.BizException;
import com.gudu.xsd.modules.feedback.domain.model.Feedback;
import com.gudu.xsd.modules.feedback.domain.model.FeedbackPage;
import com.gudu.xsd.modules.feedback.domain.model.FeedbackStatus;
import com.gudu.xsd.modules.feedback.domain.repository.FeedbackRepository;
import com.gudu.xsd.modules.member.Member;
import com.gudu.xsd.modules.member.MemberService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FeedbackAppServiceTest {

    static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    static final LocalDate TODAY = LocalDate.of(2026, 10, 16);

    FeedbackRepository repo;
    MemberService memberService;
    FeedbackAppService svc;

    @BeforeEach
    void setUp() {
        repo = mock(FeedbackRepository.class);
        memberService = mock(MemberService.class);
        svc = new FeedbackAppService(repo, memberService,
                Clock.fixed(TODAY.atTime(9, 0).atZone(ZONE).toInstant(), ZONE));
    }

    @Test
    void 提交_保存返回id_限流按当天0点起算() {
        doAnswer(inv -> {
            inv.getArgument(0, Feedback.class).identify(8L);
            return null;
        }).when(repo).save(any());
        assertThat(svc.submit(1L, "希望能导出菜谱", null, null, null)).isEqualTo(8L);
        verify(repo).countByMemberSince(1L, TODAY.atStartOfDay());
    }

    @Test
    void 提交_当天已10条_429() {
        when(repo.countByMemberSince(eq(1L), any())).thenReturn(10L);
        assertThatThrownBy(() -> svc.submit(1L, "希望能导出菜谱", null, null, null))
                .isInstanceOfSatisfying(BizException.class, e -> assertThat(e.getCode()).isEqualTo(429));
        verify(repo, never()).save(any());
    }

    @Test
    void 列表_回填成员名_分页参数夹紧_状态过滤() {
        Feedback f = Feedback.rehydrate(3L, 1L, "内容够长了", null, List.of(), null, FeedbackStatus.NEW,
                LocalDateTime.of(2026, 10, 16, 8, 0));
        when(repo.page(eq(FeedbackStatus.NEW), anyInt(), anyInt())).thenReturn(new FeedbackPage(List.of(f), 1, 1, 100));
        Member m = new Member();
        m.setId(1L);
        m.setName("张爸爸");
        when(memberService.listByIds(any())).thenReturn(List.of(m));

        IPage<FeedbackView> p = svc.page("NEW", 0, 1000);

        verify(repo).page(FeedbackStatus.NEW, 1, 100);
        assertThat(p.getTotal()).isEqualTo(1);
        assertThat(p.getRecords().get(0).memberName()).isEqualTo("张爸爸");
        assertThat(p.getRecords().get(0).status()).isEqualTo("NEW");
    }

    @Test
    void 列表_不传状态_空页不查成员() {
        when(repo.page(null, 1, 10)).thenReturn(new FeedbackPage(List.of(), 0, 1, 10));
        assertThat(svc.page(null, null, null).getRecords()).isEmpty();
        verify(memberService, never()).listByIds(any());
    }

    @Test
    void 改状态_存在则保存_不存在报错() {
        Feedback f = Feedback.rehydrate(3L, 1L, "内容够长了", null, List.of(), null, FeedbackStatus.NEW, null);
        when(repo.findById(3L)).thenReturn(Optional.of(f));
        svc.changeStatus(3L, "DONE");
        assertThat(f.status()).isEqualTo(FeedbackStatus.DONE);
        verify(repo).save(f);
        assertThatThrownBy(() -> svc.changeStatus(4L, "DONE")).hasMessageContaining("反馈不存在");
    }
}
