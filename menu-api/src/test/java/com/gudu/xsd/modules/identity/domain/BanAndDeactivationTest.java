package com.gudu.xsd.modules.identity.domain;

import com.gudu.xsd.common.domain.DomainException;
import com.gudu.xsd.modules.identity.domain.model.AccountBan;
import com.gudu.xsd.modules.identity.domain.model.AccountDeactivation;
import com.gudu.xsd.modules.identity.domain.model.DeactivationStatus;
import com.gudu.xsd.modules.identity.domain.model.ErasureStepResult;
import com.gudu.xsd.modules.identity.domain.model.PlatformRole;
import com.gudu.xsd.modules.identity.domain.service.WorkingDays;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** F.1 封禁规则 / F.2 注销处理记录与 15 个工作日时限。 */
class BanAndDeactivationTest {

    static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 26, 10, 0); // 周一

    @Test
    void 封禁_原因必填且不超200字_不能封自己_超管不可封_运营只能由超管封() {
        AccountBan b = AccountBan.impose(5L, PlatformRole.USER, 1L, PlatformRole.OPERATOR, " 发布违规内容 ", NOW);
        assertThat(b.reason()).isEqualTo("发布违规内容");
        assertThat(b.bannedBy()).isEqualTo(1L);
        assertThatThrownBy(() -> AccountBan.impose(5L, PlatformRole.USER, 1L, PlatformRole.OPERATOR, " ", NOW))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> AccountBan.impose(5L, PlatformRole.USER, 1L, PlatformRole.OPERATOR, "x".repeat(201), NOW))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> AccountBan.impose(1L, PlatformRole.USER, 1L, PlatformRole.SUPER_ADMIN, "r", NOW))
                .hasMessageContaining("自己");
        assertThatThrownBy(() -> AccountBan.impose(5L, PlatformRole.SUPER_ADMIN, 1L, PlatformRole.SUPER_ADMIN, "r", NOW))
                .hasMessageContaining("超级管理员");
        assertThatThrownBy(() -> AccountBan.impose(5L, PlatformRole.OPERATOR, 1L, PlatformRole.OPERATOR, "r", NOW))
                .hasMessageContaining("超级管理员");
        assertThat(AccountBan.impose(5L, PlatformRole.OPERATOR, 1L, PlatformRole.SUPER_ADMIN, "r", NOW).accountId())
                .isEqualTo(5L);
    }

    @Test
    void 工作日_跳过周末_截止为第15个工作日结束_超期分界() {
        // 周一受理：+15 个工作日 = 三周后的周一
        assertThat(WorkingDays.deadline(NOW, 15)).isEqualTo(LocalDateTime.of(2026, 11, 16, 23, 59, 59));
        // 周五受理：下周一算第 1 个
        assertThat(WorkingDays.deadline(LocalDateTime.of(2026, 10, 30, 18, 0), 1))
                .isEqualTo(LocalDateTime.of(2026, 11, 2, 23, 59, 59));
        LocalDateTime now = LocalDateTime.of(2026, 11, 17, 9, 0);
        LocalDateTime threshold = WorkingDays.overdueThreshold(now);
        // 分界与逐条判断一致：分界前一刻受理的已超期，分界当刻受理的未超期
        assertThat(WorkingDays.overdue(threshold.minusSeconds(1), false, now)).isTrue();
        assertThat(WorkingDays.overdue(threshold, false, now)).isFalse();
        assertThat(WorkingDays.overdue(NOW, false, now)).isTrue();
        assertThat(WorkingDays.overdue(NOW, true, now)).as("已完成不算超期").isFalse();
    }

    @Test
    void 注销记录_受理即记第一步_后续全部成功DONE_有失败FAILED可重试_完成后不能重试() {
        AccountDeactivation d = AccountDeactivation.accept(9L, NOW);
        assertThat(d.status()).isEqualTo(DeactivationStatus.PENDING);
        assertThat(d.steps()).singleElement().satisfies(s -> {
            assertThat(s.name()).isEqualTo(AccountDeactivation.STEP_ACCOUNT);
            assertThat(s.ok()).isTrue();
        });
        d.applyAttempt(List.of(ErasureStepResult.failed("意见反馈联系方式清除", "DB 超时")), NOW.plusMinutes(1));
        assertThat(d.status()).isEqualTo(DeactivationStatus.FAILED);
        assertThat(d.lastError()).contains("意见反馈联系方式清除").contains("DB 超时");
        assertThat(d.attempts()).isEqualTo(1);

        d.applyAttempt(List.of(ErasureStepResult.ok("意见反馈联系方式清除", "清除 2 条")), NOW.plusDays(1));
        assertThat(d.status()).isEqualTo(DeactivationStatus.DONE);
        assertThat(d.steps()).hasSize(2).allMatch(ErasureStepResult::ok);
        assertThat(d.lastError()).isNull();
        assertThat(d.completedAt()).isEqualTo(NOW.plusDays(1));
        assertThat(d.overdue(NOW.plusYears(1))).isFalse();
        assertThatThrownBy(d::ensureRetryable).isInstanceOf(DomainException.class);
    }

    @Test
    void 注销记录_超期判断() {
        AccountDeactivation d = AccountDeactivation.accept(9L, NOW);
        assertThat(d.deadline()).isEqualTo(LocalDateTime.of(2026, 11, 16, 23, 59, 59));
        assertThat(d.overdue(LocalDateTime.of(2026, 11, 16, 20, 0))).isFalse();
        assertThat(d.overdue(LocalDateTime.of(2026, 11, 17, 0, 0))).isTrue();
    }
}
