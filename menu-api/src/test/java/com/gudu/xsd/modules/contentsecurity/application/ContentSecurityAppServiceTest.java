package com.gudu.xsd.modules.contentsecurity.application;

import com.gudu.xsd.common.BizException;
import com.gudu.xsd.common.domain.DomainEventPublisher;
import com.gudu.xsd.config.WxProperties;
import com.gudu.xsd.modules.contentsecurity.domain.event.MediaBlockedEvent;
import com.gudu.xsd.modules.contentsecurity.domain.model.ContentPurpose;
import com.gudu.xsd.modules.contentsecurity.domain.model.ContentScene;
import com.gudu.xsd.modules.contentsecurity.domain.model.MediaCheck;
import com.gudu.xsd.modules.contentsecurity.domain.model.MediaCheckRequest;
import com.gudu.xsd.modules.contentsecurity.domain.model.MediaCheckResult;
import com.gudu.xsd.modules.contentsecurity.domain.model.MediaCheckStatus;
import com.gudu.xsd.modules.contentsecurity.domain.model.Suggest;
import com.gudu.xsd.modules.contentsecurity.domain.model.TextCheck;
import com.gudu.xsd.modules.contentsecurity.domain.model.TextVerdict;
import com.gudu.xsd.modules.contentsecurity.domain.port.ContentSecurity;
import com.gudu.xsd.modules.contentsecurity.domain.port.ContentSecurityUnavailableException;
import com.gudu.xsd.modules.contentsecurity.domain.port.OpenidResolver;
import com.gudu.xsd.modules.contentsecurity.domain.port.UploadQuarantine;
import com.gudu.xsd.modules.contentsecurity.domain.repository.MediaCheckRepository;
import com.gudu.xsd.modules.contentsecurity.domain.service.WxPushSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 内容安全路由与失败策略：微信通过 / 违规 / 不可用三分支 × fail-closed（昵称头像）与降级（AI、反馈、社区）。 */
class ContentSecurityAppServiceTest {

    static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    static final Instant NOW = Instant.parse("2026-10-26T02:00:00Z");
    static final Clock CLOCK = Clock.fixed(NOW, ZONE);
    static final long WX_USER = 1L;      // 小程序账号（有 openid）
    static final long APP_USER = 2L;     // App / 网页账号（无 openid）
    static final String IMG = "/gudu/uploads/original/20261026/a.jpg";

    ContentSecurity wechat;
    OpenidResolver openids;
    MediaCheckRepository repo;
    UploadQuarantine quarantine;
    DomainEventPublisher events;
    List<Object> published;
    WxProperties wx;

    @BeforeEach
    void setUp() {
        wechat = mock(ContentSecurity.class);
        openids = mock(OpenidResolver.class);
        when(openids.openidOf(WX_USER)).thenReturn(Optional.of("o-wx-user"));
        when(openids.openidOf(APP_USER)).thenReturn(Optional.empty());
        repo = mock(MediaCheckRepository.class);
        quarantine = mock(UploadQuarantine.class);
        published = new ArrayList<>();
        events = published::add;
        wx = new WxProperties();
        wx.setAppid("wx-app");
        wx.setToken("tok");
    }

    ContentSecurityAppService svc(boolean wxEnabled) {
        return new ContentSecurityAppService(wechat, openids, repo, quarantine, events, Runnable::run, wx, CLOCK,
                wxEnabled, "https://imxf.cloud/");
    }

    static TextVerdict wx(Suggest s) {
        return new TextVerdict(s, s == Suggest.PASS ? 100 : 20002, null, TextVerdict.Source.WX, "t1");
    }

    @Nested
    class 文本 {

        @Test
        void 微信通过_放行且带openid与场景() {
            when(wechat.checkText(any())).thenReturn(wx(Suggest.PASS));
            assertThatCode(() -> svc(true).assertText(WX_USER, ContentPurpose.NICKNAME, "小厨神")).doesNotThrowAnyException();
            ArgumentCaptor<TextCheck> c = ArgumentCaptor.forClass(TextCheck.class);
            verify(wechat).checkText(c.capture());
            assertThat(c.getValue().openid()).isEqualTo("o-wx-user");
            assertThat(c.getValue().scene()).isEqualTo(ContentScene.PROFILE);
        }

        @Test
        void 微信判违规_451_且提示不暴露标签() {
            when(wechat.checkText(any())).thenReturn(wx(Suggest.RISKY));
            assertThatThrownBy(() -> svc(true).assertText(WX_USER, ContentPurpose.NICKNAME, "某昵称"))
                    .isInstanceOf(BizException.class)
                    .hasMessage(ContentSecurityAppService.MSG_NICKNAME)
                    .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo(BizException.CONTENT_BLOCKED));
        }

        @Test
        void 疑似review_昵称拦截_反馈放行() {
            when(wechat.checkText(any())).thenReturn(wx(Suggest.REVIEW));
            assertThatThrownBy(() -> svc(true).assertText(WX_USER, ContentPurpose.NICKNAME, "某昵称"))
                    .isInstanceOf(BizException.class);
            assertThatCode(() -> svc(true).assertText(WX_USER, ContentPurpose.FEEDBACK, "希望加个导出"))
                    .doesNotThrowAnyException();
        }

        @Test
        void 微信不可用_昵称failClosed_452() {
            when(wechat.checkText(any())).thenThrow(new ContentSecurityUnavailableException("45009 频率限制"));
            assertThatThrownBy(() -> svc(true).assertText(WX_USER, ContentPurpose.NICKNAME, "小厨神"))
                    .isInstanceOf(BizException.class)
                    .hasMessage(ContentSecurityAppService.MSG_UNAVAILABLE)
                    .satisfies(e -> assertThat(((BizException) e).getCode())
                            .isEqualTo(BizException.CONTENT_CHECK_UNAVAILABLE));
        }

        @Test
        void 微信不可用_反馈与社区降级到规则() {
            when(wechat.checkText(any())).thenThrow(new ContentSecurityUnavailableException("timeout"));
            ContentSecurityAppService s = svc(true);
            TextVerdict v = s.checkText(WX_USER, ContentPurpose.FEEDBACK, "希望加个导出");
            assertThat(v.source()).isEqualTo(TextVerdict.Source.RULE_FALLBACK);
            assertThat(v.suggest()).isEqualTo(Suggest.PASS);
            assertThatCode(() -> s.assertText(WX_USER, ContentPurpose.POST, "清蒸鲈鱼")).doesNotThrowAnyException();
        }

        @Test
        void 规则先行_命中禁发词不调微信() {
            assertThatThrownBy(() -> svc(true).assertText(WX_USER, ContentPurpose.COMMENT, "来网赌吧"))
                    .isInstanceOf(BizException.class)
                    .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo(BizException.CONTENT_BLOCKED));
            verify(wechat, never()).checkText(any());
        }

        @Test
        void 无openid或开关关闭_只走规则() {
            assertThatCode(() -> svc(true).assertText(APP_USER, ContentPurpose.NICKNAME, "小厨神"))
                    .doesNotThrowAnyException();
            assertThatCode(() -> svc(false).assertText(WX_USER, ContentPurpose.NICKNAME, "小厨神"))
                    .doesNotThrowAnyException();
            assertThatThrownBy(() -> svc(false).assertText(WX_USER, ContentPurpose.NICKNAME, "毒品哥"))
                    .isInstanceOf(BizException.class);
            verify(wechat, never()).checkText(any());
        }

        @Test
        void 空文本直接通过() {
            assertThat(svc(true).checkText(WX_USER, ContentPurpose.FEEDBACK, " ").suggest()).isEqualTo(Suggest.PASS);
            verify(wechat, never()).checkText(any());
        }
    }

    @Nested
    class AI对话 {

        @Test
        void 输入_违规返回提示_通过为空() {
            when(wechat.checkText(any())).thenReturn(wx(Suggest.RISKY), wx(Suggest.PASS));
            assertThat(svc(true).agentInputViolation(WX_USER, "坏话"))
                    .contains(ContentSecurityAppService.AGENT_INPUT_BLOCKED);
            assertThat(svc(true).agentInputViolation(WX_USER, "今晚吃啥")).isEmpty();
        }

        @Test
        void 输入_微信不可用降级规则_不拒绝正常输入_规则词仍拦() {
            when(wechat.checkText(any())).thenThrow(new ContentSecurityUnavailableException("down"));
            assertThat(svc(true).agentInputViolation(WX_USER, "今晚吃啥")).isEmpty();
            assertThat(svc(true).agentInputViolation(WX_USER, "哪里买冰毒")).isPresent();
        }

        @Test
        void 输出_违规替换为安全提示_通过原样() {
            when(wechat.checkText(any())).thenReturn(wx(Suggest.RISKY), wx(Suggest.PASS));
            assertThat(svc(true).reviewAgentReply(WX_USER, "违规回复"))
                    .isEqualTo(ContentSecurityAppService.AGENT_REPLY_REPLACED);
            assertThat(svc(true).reviewAgentReply(WX_USER, "番茄炒蛋不错")).isEqualTo("番茄炒蛋不错");
        }

        @Test
        void 输出_微信不可用降级规则() {
            when(wechat.checkText(any())).thenThrow(new ContentSecurityUnavailableException("down"));
            assertThat(svc(true).reviewAgentReply(WX_USER, "番茄炒蛋不错")).isEqualTo("番茄炒蛋不错");
        }
    }

    @Nested
    class 图片 {

        @Test
        void 提交成功_记PENDING_地址拼公网前缀() {
            when(wechat.submitMedia(any())).thenReturn("trace-1");
            Optional<MediaCheck> c = svc(true).submitImage(WX_USER, IMG, ContentPurpose.UPLOAD);
            assertThat(c).get().extracting(MediaCheck::status).isEqualTo(MediaCheckStatus.PENDING);
            ArgumentCaptor<MediaCheckRequest> req = ArgumentCaptor.forClass(MediaCheckRequest.class);
            verify(wechat).submitMedia(req.capture());
            assertThat(req.getValue().mediaUrl()).isEqualTo("https://imxf.cloud" + IMG);
            assertThat(req.getValue().openid()).isEqualTo("o-wx-user");
            verify(repo).save(c.get());
        }

        @Test
        void 提交不可用_记SUBMIT_FAILED() {
            when(wechat.submitMedia(any())).thenThrow(new ContentSecurityUnavailableException("down"));
            assertThat(svc(true).submitImage(WX_USER, IMG, ContentPurpose.UPLOAD)).get()
                    .extracting(MediaCheck::status).isEqualTo(MediaCheckStatus.SUBMIT_FAILED);
        }

        @Test
        void 异步提交_关闭或无openid或外链_不提交() {
            svc(false).submitImageLater(WX_USER, IMG, ContentPurpose.UPLOAD);
            svc(true).submitImageLater(APP_USER, IMG, ContentPurpose.UPLOAD);
            svc(true).submitImageLater(WX_USER, "https://evil.com/a.jpg", ContentPurpose.UPLOAD);
            verify(wechat, never()).submitMedia(any());
            verify(repo, never()).save(any());
        }

        @Test
        void 异步提交_异常不外抛() {
            when(wechat.submitMedia(any())).thenThrow(new IllegalStateException("boom"));
            assertThatCode(() -> svc(true).submitImageLater(WX_USER, IMG, ContentPurpose.UPLOAD))
                    .doesNotThrowAnyException();
        }

        @Test
        void 头像_已判违规_451() {
            when(repo.latestByUrl(IMG)).thenReturn(Optional.of(check(MediaCheckStatus.RISKY)));
            assertThatThrownBy(() -> svc(true).assertImageUsable(APP_USER, IMG, ContentPurpose.AVATAR))
                    .isInstanceOf(BizException.class).hasMessage(ContentSecurityAppService.MSG_AVATAR);
        }

        @Test
        void 头像_待回调放行_不重复提交() {
            when(repo.latestByUrl(IMG)).thenReturn(Optional.of(check(MediaCheckStatus.PENDING)));
            assertThatCode(() -> svc(true).assertImageUsable(WX_USER, IMG, ContentPurpose.AVATAR))
                    .doesNotThrowAnyException();
            verify(wechat, never()).submitMedia(any());
        }

        @Test
        void 头像_无记录或提交失败_同步补提交_仍不可用则452() {
            when(repo.latestByUrl(IMG)).thenReturn(Optional.of(check(MediaCheckStatus.SUBMIT_FAILED)));
            when(wechat.submitMedia(any())).thenThrow(new ContentSecurityUnavailableException("down"));
            assertThatThrownBy(() -> svc(true).assertImageUsable(WX_USER, IMG, ContentPurpose.AVATAR))
                    .isInstanceOf(BizException.class)
                    .satisfies(e -> assertThat(((BizException) e).getCode())
                            .isEqualTo(BizException.CONTENT_CHECK_UNAVAILABLE));
        }

        @Test
        void 头像_无记录_补提交成功放行() {
            when(repo.latestByUrl(IMG)).thenReturn(Optional.empty());
            when(wechat.submitMedia(any())).thenReturn("trace-2");
            assertThatCode(() -> svc(true).assertImageUsable(WX_USER, IMG, ContentPurpose.AVATAR))
                    .doesNotThrowAnyException();
            verify(wechat).submitMedia(any());
        }

        @Test
        void 头像_未配置公网前缀_failClosed() {
            when(repo.latestByUrl(IMG)).thenReturn(Optional.empty());
            ContentSecurityAppService s = new ContentSecurityAppService(wechat, openids, repo, quarantine, events,
                    Runnable::run, wx, CLOCK, true, "");
            assertThatThrownBy(() -> s.assertImageUsable(WX_USER, IMG, ContentPurpose.AVATAR))
                    .isInstanceOf(BizException.class);
            verify(wechat, never()).submitMedia(any());
        }

        @Test
        void 头像_App账号无openid_无记录放行() {
            when(repo.latestByUrl(IMG)).thenReturn(Optional.empty());
            assertThatCode(() -> svc(true).assertImageUsable(APP_USER, IMG, ContentPurpose.AVATAR))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    class 回调 {

        String body(String suggest) {
            return "{\"ToUserName\":\"gh_x\",\"FromUserName\":\"o-wx-user\",\"CreateTime\":1,\"MsgType\":\"event\","
                    + "\"Event\":\"wxa_media_check\",\"appid\":\"wx-app\",\"trace_id\":\"trace-1\",\"version\":2,"
                    + "\"detail\":[],\"errcode\":0,\"errmsg\":\"ok\",\"result\":{\"suggest\":\"" + suggest
                    + "\",\"label\":20002}}";
        }

        String ts() {
            return String.valueOf(NOW.getEpochSecond());
        }

        @Test
        void 签名不对或token未配置_拒绝且不处理() {
            assertThat(svc(true).handlePush("bad", ts(), "n", body("risky"))).isFalse();
            wx.setToken("");
            assertThat(svc(true).handlePush(WxPushSignature.sign("", ts(), "n"), ts(), "n", body("risky"))).isFalse();
            verify(repo, never()).findByTraceId(any());
        }

        @Test
        void 违规_定论RISKY_隔离文件_发布事件_重复推送幂等() {
            MediaCheck c = check(MediaCheckStatus.PENDING);
            when(repo.findByTraceId("trace-1")).thenReturn(Optional.of(c));
            when(quarantine.quarantine(IMG)).thenReturn(true);
            ContentSecurityAppService s = svc(true);
            assertThat(s.handlePush(WxPushSignature.sign("tok", ts(), "n"), ts(), "n", body("risky"))).isTrue();
            assertThat(c.status()).isEqualTo(MediaCheckStatus.RISKY);
            verify(quarantine).quarantine(IMG);
            assertThat(published).singleElement().isInstanceOf(MediaBlockedEvent.class);

            s.onMediaCheckResult(new MediaCheckResult("wx-app", "trace-1", Suggest.RISKY, 20002, 0));
            verify(quarantine).quarantine(IMG); // 仍只一次
            assertThat(published).hasSize(1);
        }

        @Test
        void 通过_定论PASS_不隔离() {
            MediaCheck c = check(MediaCheckStatus.PENDING);
            when(repo.findByTraceId("trace-1")).thenReturn(Optional.of(c));
            assertThat(svc(true).handlePush(WxPushSignature.sign("tok", ts(), "n"), ts(), "n", body("pass"))).isTrue();
            assertThat(c.status()).isEqualTo(MediaCheckStatus.PASS);
            verify(quarantine, never()).quarantine(any());
        }

        @Test
        void 其它小程序或未知trace或非检测事件_忽略() {
            svc(true).onMediaCheckResult(new MediaCheckResult("wx-other", "trace-1", Suggest.RISKY, 1, 0));
            verify(repo, never()).findByTraceId(any());
            when(repo.findByTraceId("nope")).thenReturn(Optional.empty());
            svc(true).onMediaCheckResult(new MediaCheckResult("wx-app", "nope", Suggest.RISKY, 1, 0));
            verify(quarantine, never()).quarantine(any());
            assertThat(ContentSecurityAppService.parseMediaCheck("{\"Event\":\"user_enter_tempsession\"}")).isEmpty();
            assertThat(ContentSecurityAppService.parseMediaCheck("<xml/>")).isEmpty();
        }
    }

    static MediaCheck check(MediaCheckStatus status) {
        return MediaCheck.rehydrate(9L, "trace-1", IMG, WX_USER, ContentPurpose.UPLOAD, status, 0,
                LocalDateTime.of(2026, 10, 26, 10, 0));
    }
}
