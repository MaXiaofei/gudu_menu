package com.gudu.xsd.modules.contentsecurity.application;

import com.gudu.xsd.common.BizException;
import com.gudu.xsd.common.domain.DomainEventPublisher;
import com.gudu.xsd.common.upload.UploadUrlSigner;
import com.gudu.xsd.config.WxProperties;
import com.gudu.xsd.modules.contentsecurity.domain.model.ContentPurpose;
import com.gudu.xsd.modules.contentsecurity.domain.port.ContentSecurity;
import com.gudu.xsd.modules.contentsecurity.domain.port.OpenidResolver;
import com.gudu.xsd.modules.contentsecurity.domain.port.UploadQuarantine;
import com.gudu.xsd.modules.contentsecurity.domain.repository.MediaCheckRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * 补全覆盖的入口辅助：assertUserText（成员称呼 / 菜谱 / 点评 / 食集名 / 家庭名）与 reviewAiText（AI 生成文本）。
 * 单测无 Web 上下文 = 未登录，只走规则；微信路由与降级由 {@link ContentSecurityAppServiceTest} 覆盖。
 */
class ContentSecurityCoverageTest {

    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-26T02:00:00Z"), ZoneId.of("Asia/Shanghai"));

    final ContentSecurityAppService svc = new ContentSecurityAppService(mock(ContentSecurity.class),
            mock(OpenidResolver.class), mock(MediaCheckRepository.class), mock(UploadQuarantine.class),
            mock(DomainEventPublisher.class), Runnable::run, new WxProperties(), CLOCK, false, "",
            new UploadUrlSigner("unit-test-upload-secret-01", Duration.ofHours(24), Duration.ofDays(7), true, CLOCK),
            mock(TextViolationRecorder.class));

    @Test
    void 新增用途均不是失败即拒绝_微信不可用降级规则() {
        for (ContentPurpose p : new ContentPurpose[]{ContentPurpose.MEMBER_NAME, ContentPurpose.FAMILY_NAME,
                ContentPurpose.DISH_TEXT, ContentPurpose.MENU_NAME, ContentPurpose.REVIEW_TEXT, ContentPurpose.AI_OUTPUT}) {
            assertThat(p.failClosed()).as(p.name()).isFalse();
            assertThat(p.name().length()).as("content_text_violation.purpose 为 VARCHAR(16)").isLessThanOrEqualTo(16);
        }
    }

    @Test
    void 正常文本放行_空白与null忽略() {
        assertThatCode(() -> svc.assertUserText(ContentPurpose.DISH_TEXT, "番茄炒蛋", null, "  ", "先打散鸡蛋")).doesNotThrowAnyException();
        assertThatCode(() -> svc.assertUserText(ContentPurpose.MEMBER_NAME, (String[]) null)).doesNotThrowAnyException();
    }

    @Test
    void 任一段命中禁发词_抛451() {
        assertThatThrownBy(() -> svc.assertUserText(ContentPurpose.DISH_TEXT, "好菜", "步骤一：准备毒品"))
                .isInstanceOfSatisfying(BizException.class, e -> assertThat(e.getCode()).isEqualTo(BizException.CONTENT_BLOCKED));
        assertThatThrownBy(() -> svc.assertUserText(ContentPurpose.REVIEW_TEXT, "这是赌博网站"))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> svc.assertUserText(ContentPurpose.FAMILY_NAME, "六合彩之家"))
                .isInstanceOf(BizException.class);
    }

    @Test
    void AI生成文本_违规替换为安全提示_正常原样() {
        assertThat(svc.reviewAiText("估算基于常见份量，仅供参考")).isEqualTo("估算基于常见份量，仅供参考");
        assertThat(svc.reviewAiText("推荐你试试大麻烟")).isEqualTo(ContentSecurityAppService.AI_TEXT_REPLACED);
        assertThat(svc.reviewAiText(null)).isNull();
        assertThat(svc.reviewAiText("  ")).isEqualTo("  ");
    }

    @Test
    void 长文本按2000字分段_违规词在后段也能命中() {
        String filler = "好".repeat(2500);
        assertThat(ContentSecurityAppService.chunks(filler, "步骤二")).hasSizeGreaterThanOrEqualTo(2);
        assertThat(ContentSecurityAppService.chunks(filler, "步骤二")).allSatisfy(c -> assertThat(c.length()).isLessThanOrEqualTo(2000));
        assertThatThrownBy(() -> svc.assertUserText(ContentPurpose.DISH_TEXT, filler + "冰毒"))
                .isInstanceOf(BizException.class);
    }
}
