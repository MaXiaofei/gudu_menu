package com.gudu.xsd.modules.contentsecurity.domain;

import com.gudu.xsd.common.domain.DomainException;
import com.gudu.xsd.common.domain.SensitiveWords;
import com.gudu.xsd.modules.contentsecurity.domain.event.MediaBlockedEvent;
import com.gudu.xsd.modules.contentsecurity.domain.model.ContentPurpose;
import com.gudu.xsd.modules.contentsecurity.domain.model.ContentScene;
import com.gudu.xsd.modules.contentsecurity.domain.model.MediaCheck;
import com.gudu.xsd.modules.contentsecurity.domain.model.MediaCheckRequest;
import com.gudu.xsd.modules.contentsecurity.domain.model.MediaCheckResult;
import com.gudu.xsd.modules.contentsecurity.domain.model.MediaCheckStatus;
import com.gudu.xsd.modules.contentsecurity.domain.model.Suggest;
import com.gudu.xsd.modules.contentsecurity.domain.model.TextCheck;
import com.gudu.xsd.modules.contentsecurity.domain.port.ContentSecurityUnavailableException;
import com.gudu.xsd.modules.contentsecurity.domain.service.RuleContentSecurity;
import com.gudu.xsd.modules.contentsecurity.domain.service.WxPushSignature;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContentSecurityDomainTest {

    static final LocalDateTime T = LocalDateTime.of(2026, 10, 26, 10, 0);

    @Test
    void 规则实现_禁发词RISKY_正常PASS_食物不误伤_不支持图片() {
        RuleContentSecurity rule = new RuleContentSecurity();
        assertThat(rule.checkText(new TextCheck("哪里有博彩", ContentScene.COMMENT, null)).suggest())
                .isEqualTo(Suggest.RISKY);
        for (String food : new String[]{"黄油煎蛋", "红烧肉", "麻辣香锅", "大盘鸡", "冰糖雪梨"}) {
            assertThat(rule.checkText(new TextCheck(food, ContentScene.SOCIAL_LOG, null)).suggest())
                    .as(food).isEqualTo(Suggest.PASS);
        }
        assertThatThrownBy(() -> rule.submitMedia(new MediaCheckRequest("u", ContentScene.PROFILE, "o")))
                .isInstanceOf(ContentSecurityUnavailableException.class);
        assertThat(SensitiveWords.firstHit(null)).isEmpty();
    }

    @Test
    void 用途_失败策略与疑似拦截() {
        assertThat(ContentPurpose.NICKNAME.failClosed()).isTrue();
        assertThat(ContentPurpose.AVATAR.failClosed()).isTrue();
        assertThat(ContentPurpose.AGENT_INPUT.failClosed()).isFalse();
        assertThat(ContentPurpose.FEEDBACK.failClosed()).isFalse();
        assertThat(Suggest.REVIEW.blocks(ContentPurpose.NICKNAME)).isTrue();
        assertThat(Suggest.REVIEW.blocks(ContentPurpose.AGENT_OUTPUT)).isFalse();
        assertThat(Suggest.RISKY.blocks(ContentPurpose.UPLOAD)).isTrue();
        assertThat(Suggest.ofWx("weird")).isEqualTo(Suggest.REVIEW);
        assertThat(ContentPurpose.ofUploadUsage("Avatar")).isEqualTo(ContentPurpose.AVATAR);
        assertThat(ContentPurpose.ofUploadUsage(null)).isEqualTo(ContentPurpose.UPLOAD);
        assertThat(ContentPurpose.AVATAR.scene().wxCode()).isEqualTo(1);
    }

    @Test
    void 图片检测_回调定论_违规登记事件_幂等() {
        MediaCheck c = MediaCheck.submitted("tr", "/gudu/uploads/a.jpg", 1L, ContentPurpose.UPLOAD, T);
        assertThat(c.resolve(new MediaCheckResult("wx", "tr", Suggest.RISKY, 20002, 0))).isTrue();
        assertThat(c.status()).isEqualTo(MediaCheckStatus.RISKY);
        assertThat(c.blocked()).isTrue();
        assertThat(c.peekDomainEvents()).singleElement().isInstanceOf(MediaBlockedEvent.class);
        assertThat(((MediaBlockedEvent) c.peekDomainEvents().get(0)).urlVariants())
                .containsExactly("/gudu/uploads/a.jpg");
        assertThat(new MediaBlockedEvent("/gudu/uploads/original/1/a.jpg", 1L, "UPLOAD", 1).urlVariants())
                .containsExactly("/gudu/uploads/original/1/a.jpg", "/gudu/uploads/thumbnail/1/a.jpg");
        assertThat(c.resolve(new MediaCheckResult("wx", "tr", Suggest.PASS, 100, 0))).isFalse();
        assertThat(c.status()).isEqualTo(MediaCheckStatus.RISKY);
    }

    @Test
    void 图片检测_头像疑似也拦_检测失败可重提() {
        MediaCheck avatar = MediaCheck.submitted("tr", "/gudu/uploads/a.jpg", 1L, ContentPurpose.AVATAR, T);
        assertThat(avatar.resolve(new MediaCheckResult("wx", "tr", Suggest.REVIEW, 10001, 0))).isTrue();
        MediaCheck failed = MediaCheck.submitted("tr2", "/gudu/uploads/b.jpg", 1L, ContentPurpose.UPLOAD, T);
        assertThat(failed.resolve(new MediaCheckResult("wx", "tr2", Suggest.PASS, 0, 1))).isFalse();
        assertThat(failed.status()).isEqualTo(MediaCheckStatus.CHECK_FAILED);
        assertThat(failed.needsResubmit()).isTrue();
        assertThat(MediaCheck.submitFailed("/gudu/uploads/c.jpg", 1L, ContentPurpose.AVATAR, T).needsResubmit()).isTrue();
        assertThatThrownBy(() -> MediaCheck.submitted("", "/u", 1L, ContentPurpose.UPLOAD, T))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void 推送签名_正确通过_错token错签名过期均拒绝() {
        long now = 1_790_000_000L;
        String ts = String.valueOf(now);
        String sig = WxPushSignature.sign("tok", ts, "nonce");
        assertThat(WxPushSignature.verify("tok", ts, "nonce", sig, now)).isTrue();
        assertThat(WxPushSignature.verify("tok", ts, "nonce", sig.toUpperCase(), now + 10)).isTrue();
        assertThat(WxPushSignature.verify("other", ts, "nonce", sig, now)).isFalse();
        assertThat(WxPushSignature.verify("tok", ts, "nonce", "deadbeef", now)).isFalse();
        assertThat(WxPushSignature.verify("tok", ts, "nonce", sig, now + WxPushSignature.MAX_SKEW_SECONDS + 1)).isFalse();
        assertThat(WxPushSignature.verify("", ts, "nonce", WxPushSignature.sign("", ts, "nonce"), now)).isFalse();
        assertThat(WxPushSignature.verify("tok", "abc", "nonce", sig, now)).isFalse();
    }
}
