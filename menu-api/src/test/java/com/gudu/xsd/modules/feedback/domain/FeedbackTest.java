package com.gudu.xsd.modules.feedback.domain;

import com.gudu.xsd.common.domain.DomainException;
import com.gudu.xsd.modules.feedback.domain.model.Feedback;
import com.gudu.xsd.modules.feedback.domain.model.FeedbackStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FeedbackTest {

    static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 16, 10, 0);

    @Test
    void 提交_合法_默认NEW_空白字段归null() {
        Feedback f = Feedback.submit(1L, "  希望能导出菜谱  ", " ", Arrays.asList("/gudu/uploads/a.jpg", " ", null),
                "mini 1.2.0", NOW);
        assertThat(f.content()).isEqualTo("希望能导出菜谱");
        assertThat(f.contact()).isNull();
        assertThat(f.images()).containsExactly("/gudu/uploads/a.jpg");
        assertThat(f.status()).isEqualTo(FeedbackStatus.NEW);
        assertThat(f.createTime()).isEqualTo(NOW);
        assertThat(f.memberId()).isEqualTo(1L);
        assertThat(f.clientInfo()).isEqualTo("mini 1.2.0");
    }

    @Test
    void 提交_内容长度_联系方式_图片数量与地址校验() {
        assertThatThrownBy(() -> Feedback.submit(1L, "太短", null, null, null, NOW))
                .isInstanceOf(DomainException.class).hasMessageContaining("5–500");
        assertThatThrownBy(() -> Feedback.submit(1L, "x".repeat(501), null, null, null, NOW))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> Feedback.submit(1L, null, null, null, null, NOW)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> Feedback.submit(1L, "内容够长了", "c".repeat(51), null, null, NOW))
                .isInstanceOf(DomainException.class).hasMessageContaining("联系方式");
        List<String> four = List.of("/uploads/1.jpg", "/uploads/2.jpg", "/uploads/3.jpg", "/uploads/4.jpg");
        assertThatThrownBy(() -> Feedback.submit(1L, "内容够长了", null, four, null, NOW))
                .isInstanceOf(DomainException.class).hasMessageContaining("3 张");
        assertThatThrownBy(() -> Feedback.submit(1L, "内容够长了", null, List.of("http://x.com/a.png"), null, NOW))
                .isInstanceOf(DomainException.class).hasMessageContaining("本站");
        assertThatThrownBy(() -> Feedback.submit(null, "内容够长了", null, null, null, NOW))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void 客户端信息超长截断_状态流转_id回填() {
        Feedback f = Feedback.submit(1L, "内容够长了", null, null, "c".repeat(300), NOW);
        assertThat(f.clientInfo()).hasSize(200);
        f.changeStatus(FeedbackStatus.DONE);
        assertThat(f.status()).isEqualTo(FeedbackStatus.DONE);
        assertThatThrownBy(() -> f.changeStatus(null)).isInstanceOf(DomainException.class);
        f.identify(3L);
        assertThat(f.id()).isEqualTo(3L);
        assertThatThrownBy(() -> f.identify(4L)).isInstanceOf(DomainException.class);
        assertThat(FeedbackStatus.of("DONE")).isEqualTo(FeedbackStatus.DONE);
        assertThatThrownBy(() -> FeedbackStatus.of("OPEN")).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> FeedbackStatus.of(null)).isInstanceOf(DomainException.class);
        assertThat(Feedback.rehydrate(1L, 1L, "c", null, null, null, null, NOW).status()).isEqualTo(FeedbackStatus.NEW);
    }
}
