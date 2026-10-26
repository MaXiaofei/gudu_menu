package com.gudu.xsd.modules.identity.application;

import com.gudu.xsd.common.privacy.UploadedFileEraser;
import com.gudu.xsd.modules.agent.application.privacy.AgentAccountErasureStep;
import com.gudu.xsd.modules.agent.domain.repository.AgentMessageRepository;
import com.gudu.xsd.modules.agent.domain.repository.AgentSessionRepository;
import com.gudu.xsd.modules.agent.domain.repository.AgentTraceRepository;
import com.gudu.xsd.modules.contentsecurity.application.AvatarFileErasureStep;
import com.gudu.xsd.modules.contentsecurity.domain.model.ContentPurpose;
import com.gudu.xsd.modules.contentsecurity.domain.repository.MediaCheckRepository;
import com.gudu.xsd.modules.feedback.application.FeedbackAccountErasureStep;
import com.gudu.xsd.modules.feedback.domain.repository.FeedbackRepository;
import com.gudu.xsd.modules.identity.application.dto.DeactivationView;
import com.gudu.xsd.modules.identity.domain.event.AccountDeactivated;
import com.gudu.xsd.modules.identity.domain.model.DeactivationStatus;
import com.gudu.xsd.modules.notification.NotificationAccountErasureStep;
import com.gudu.xsd.modules.notification.NotificationService;
import com.gudu.xsd.modules.preference.application.PreferenceAccountErasureStep;
import com.gudu.xsd.modules.preference.domain.repository.UserPreferenceRepository;
import com.gudu.xsd.modules.privacy.application.ConsentRetainedStep;
import com.gudu.xsd.modules.privacy.application.ExportLinkErasureStep;
import com.gudu.xsd.modules.privacy.domain.repository.ConsentRepository;
import com.gudu.xsd.modules.privacy.domain.repository.ExportGuard;
import com.gudu.xsd.modules.vision.application.VisionAccountErasureStep;
import com.gudu.xsd.modules.vision.domain.model.VisionJob;
import com.gudu.xsd.modules.vision.domain.port.ImageStore;
import com.gudu.xsd.modules.vision.domain.repository.VisionJobRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 注销后续匿名化各步骤（隐私政策 §六、§七.6）：每步删/匿名的内容、幂等、失败可重试。 */
class AccountErasureStepsTest {

    static final long ACC = 9L;
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-26T02:00:00Z"), ZoneId.of("Asia/Shanghai"));

    @Test
    void AI对话_删消息和会话_含软删除_调用链去标识() {
        AgentMessageRepository messages = mock(AgentMessageRepository.class);
        AgentSessionRepository sessions = mock(AgentSessionRepository.class);
        AgentTraceRepository traces = mock(AgentTraceRepository.class);
        when(messages.deleteByMember(ACC)).thenReturn(5);
        when(sessions.idsByMember(ACC)).thenReturn(List.of("s1", "s2"));
        when(sessions.hardDelete(List.of("s1", "s2"))).thenReturn(2);
        when(traces.anonymizeByMember(ACC)).thenReturn(3);

        String r = new AgentAccountErasureStep(messages, sessions, traces).erase(ACC);

        assertThat(r).contains("消息 5").contains("会话 2").contains("调用链去标识 3");
        verify(messages).deleteBySessions(List.of("s1", "s2"));
    }

    @Test
    void AI对话_无数据时幂等() {
        AgentMessageRepository messages = mock(AgentMessageRepository.class);
        AgentSessionRepository sessions = mock(AgentSessionRepository.class);
        when(sessions.idsByMember(ACC)).thenReturn(List.of());
        String r = new AgentAccountErasureStep(messages, sessions, mock(AgentTraceRepository.class)).erase(ACC);
        assertThat(r).contains("消息 0").contains("会话 0");
        verify(messages, never()).deleteBySessions(any());
    }

    @Test
    void 反馈_先删截图文件再去标识解绑_文件删除失败不动记录() {
        FeedbackRepository repo = mock(FeedbackRepository.class);
        UploadedFileEraser files = mock(UploadedFileEraser.class);
        when(repo.imageUrlsOfAccount(ACC)).thenReturn(List.of("/gudu/uploads/original/a.png", "/gudu/uploads/original/b.png"));
        when(files.erase(any())).thenReturn(2);
        when(repo.anonymizeAccount(ACC)).thenReturn(4);

        String r = new FeedbackAccountErasureStep(repo, files).erase(ACC);

        assertThat(r).contains("反馈 4 条").contains("截图文件删除 4 个");
        InOrder order = inOrder(files, repo);
        order.verify(files).erase("/gudu/uploads/original/a.png");
        order.verify(repo).anonymizeAccount(ACC);

        FeedbackRepository repo2 = mock(FeedbackRepository.class);
        UploadedFileEraser bad = mock(UploadedFileEraser.class);
        when(repo2.imageUrlsOfAccount(ACC)).thenReturn(List.of("/gudu/uploads/original/a.png"));
        when(bad.erase(any())).thenThrow(new IllegalStateException("磁盘只读"));
        try {
            new FeedbackAccountErasureStep(repo2, bad).erase(ACC);
        } catch (IllegalStateException expected) {
            assertThat(expected).hasMessageContaining("磁盘只读");
        }
        verify(repo2, never()).anonymizeAccount(any());
    }

    @Test
    void 识别任务_删原图再删记录() {
        VisionJobRepository jobs = mock(VisionJobRepository.class);
        ImageStore images = mock(ImageStore.class);
        VisionJob withImage = mock(VisionJob.class);
        VisionJob noImage = mock(VisionJob.class);
        when(withImage.imageUrl()).thenReturn("/gudu/uploads/original/v.png");
        when(noImage.imageUrl()).thenReturn(null);
        when(jobs.findByMember(ACC)).thenReturn(List.of(withImage, noImage));
        when(jobs.deleteByMember(ACC)).thenReturn(2);

        String r = new VisionAccountErasureStep(jobs, images).erase(ACC);

        assertThat(r).contains("任务 2").contains("原图 1");
        verify(images).delete("/gudu/uploads/original/v.png");
    }

    @Test
    void 偏好_通知_导出凭证_按账号删除() {
        UserPreferenceRepository prefs = mock(UserPreferenceRepository.class);
        NotificationService notices = mock(NotificationService.class);
        ExportGuard guard = mock(ExportGuard.class);
        when(prefs.deleteByMemberId(ACC)).thenReturn(1);
        when(notices.deleteByMember(ACC)).thenReturn(7);
        when(guard.revokeAll(ACC)).thenReturn(2);

        assertThat(new PreferenceAccountErasureStep(prefs).erase(ACC)).contains("1");
        assertThat(new NotificationAccountErasureStep(notices).erase(ACC)).contains("7");
        assertThat(new ExportLinkErasureStep(guard).erase(ACC)).contains("2");
    }

    @Test
    void 头像文件_按上传记录找回地址并删除() {
        MediaCheckRepository checks = mock(MediaCheckRepository.class);
        UploadedFileEraser files = mock(UploadedFileEraser.class);
        when(checks.urlsByAccount(ACC, ContentPurpose.AVATAR)).thenReturn(List.of("/gudu/uploads/original/av1.png", "/gudu/uploads/original/av2.png"));
        when(files.erase(any())).thenReturn(2);

        String r = new AvatarFileErasureStep(checks, files).erase(ACC);

        assertThat(r).contains("头像 2 张").contains("删除文件 4 个");
        verify(files).erase("/gudu/uploads/original/av1.png");
        verify(files).erase("/gudu/uploads/original/av2.png");
    }

    @Test
    void 同意记录_保留不删_只登记条数() {
        ConsentRepository consents = mock(ConsentRepository.class);
        when(consents.history(ACC)).thenReturn(List.of());
        String r = new ConsentRetainedStep(consents).erase(ACC);
        assertThat(r).contains("保留 0 条");
        verify(consents, never()).reassignAccount(any(), any());
    }

    @Test
    void 注销记录_步骤清单齐全_一步失败标FAILED_后台重试后DONE() {
        AccountOpsAppServiceTest.FakeDeactivations repo = new AccountOpsAppServiceTest.FakeDeactivations();
        AtomicInteger feedbackCalls = new AtomicInteger();
        UploadedFileEraser files = mock(UploadedFileEraser.class);
        doAnswer(inv -> {
            if (feedbackCalls.getAndIncrement() == 0) throw new IllegalStateException("磁盘只读");
            return 1;
        }).when(files).erase(any());
        FeedbackRepository feedback = mock(FeedbackRepository.class);
        when(feedback.imageUrlsOfAccount(ACC)).thenReturn(List.of("/gudu/uploads/original/a.png"));
        AgentSessionRepository sessions = mock(AgentSessionRepository.class);
        when(sessions.idsByMember(ACC)).thenReturn(List.of());
        MediaCheckRepository checks = mock(MediaCheckRepository.class);
        when(checks.urlsByAccount(ACC, ContentPurpose.AVATAR)).thenReturn(List.of());
        ConsentRepository consents = mock(ConsentRepository.class);
        when(consents.history(ACC)).thenReturn(List.of());
        VisionJobRepository jobs = mock(VisionJobRepository.class);
        when(jobs.findByMember(ACC)).thenReturn(List.of());

        AccountDeactivationAppService svc = new AccountDeactivationAppService(repo, List.of(
                new AgentAccountErasureStep(mock(AgentMessageRepository.class), sessions, mock(AgentTraceRepository.class)),
                new VisionAccountErasureStep(jobs, mock(ImageStore.class)),
                new PreferenceAccountErasureStep(mock(UserPreferenceRepository.class)),
                new ExportLinkErasureStep(mock(ExportGuard.class)),
                new FeedbackAccountErasureStep(feedback, files),
                new AvatarFileErasureStep(checks, files),
                new NotificationAccountErasureStep(mock(NotificationService.class)),
                new ConsentRetainedStep(consents)), CLOCK);

        svc.onDeactivated(new AccountDeactivated(ACC));
        svc.afterDeactivated(new AccountDeactivated(ACC));

        assertThat(repo.saved.status()).isEqualTo(DeactivationStatus.FAILED);
        assertThat(repo.saved.lastError()).contains("意见反馈").contains("磁盘只读");
        assertThat(repo.saved.steps().stream().map(s -> s.name()).toList()).contains(
                "账号匿名化与退出家庭", "AI 对话删除", "拍照识别任务删除", "用户偏好删除", "导出链接凭证作废",
                "意见反馈联系方式与截图清除", "头像文件删除", "通知删除", "同意记录保留（法定留存）");

        DeactivationView v = svc.retry(11L);
        assertThat(v.status()).isEqualTo("DONE");
        assertThat(v.steps()).allMatch(DeactivationView.Step::ok);
    }
}
