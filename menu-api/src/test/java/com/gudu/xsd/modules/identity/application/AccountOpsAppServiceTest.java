package com.gudu.xsd.modules.identity.application;

import com.gudu.xsd.common.BizException;
import com.gudu.xsd.common.domain.DomainException;
import com.gudu.xsd.common.tenant.TenantContextHolder;
import com.gudu.xsd.modules.identity.application.dto.DeactivationView;
import com.gudu.xsd.modules.identity.domain.event.AccountDeactivated;
import com.gudu.xsd.modules.identity.domain.model.Account;
import com.gudu.xsd.modules.identity.domain.model.AccountBan;
import com.gudu.xsd.modules.identity.domain.model.AccountDeactivation;
import com.gudu.xsd.modules.identity.domain.model.AccountStatus;
import com.gudu.xsd.modules.identity.domain.model.DeactivationStatus;
import com.gudu.xsd.modules.identity.domain.model.PlatformRole;
import com.gudu.xsd.modules.identity.domain.repository.AccountBanRepository;
import com.gudu.xsd.modules.identity.domain.repository.AccountDeactivationRepository;
import com.gudu.xsd.modules.identity.domain.repository.AccountRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** F.1 封禁应用服务 / F.2 注销后续匿名化（步骤失败不影响其他步骤、系统模式执行、后台重试）。 */
class AccountOpsAppServiceTest {

    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-26T02:00:00Z"), ZoneId.of("Asia/Shanghai"));
    static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 26, 10, 0);

    static Account account(long id, PlatformRole role, AccountStatus status) {
        return Account.rehydrate(id, null, null, null, role, status, "n" + id, null, null, null, NOW, null);
    }

    // ---------------------------------------------------------------- 封禁

    @Test
    void 封禁_校验目标与操作人角色_保存_已封禁则登录前置抛423() {
        AccountBanRepository bans = mock(AccountBanRepository.class);
        AccountRepository accounts = mock(AccountRepository.class);
        when(accounts.findById(5L)).thenReturn(Optional.of(account(5, PlatformRole.USER, AccountStatus.ACTIVE)));
        when(accounts.findById(1L)).thenReturn(Optional.of(account(1, PlatformRole.OPERATOR, AccountStatus.ACTIVE)));
        AccountBanAppService svc = new AccountBanAppService(bans, accounts, CLOCK);

        AccountBanAppService.BanView v = svc.ban(1L, 5L, "刷广告");
        assertThat(v.reason()).isEqualTo("刷广告");
        assertThat(v.bannedAt()).isEqualTo(NOW);
        verify(bans).save(any(AccountBan.class));

        when(bans.find(5L)).thenReturn(Optional.of(AccountBan.rehydrate(5L, "刷广告", 1L, NOW)));
        assertThatThrownBy(() -> svc.assertNotBanned(5L)).isInstanceOf(BizException.class)
                .hasMessageContaining("刷广告")
                .extracting(e -> ((BizException) e).getCode()).isEqualTo(BizException.ACCOUNT_BANNED);
    }

    @Test
    void 封禁_账号不存在404_已注销拒绝_解封需原因() {
        AccountBanRepository bans = mock(AccountBanRepository.class);
        AccountRepository accounts = mock(AccountRepository.class);
        when(accounts.findById(6L)).thenReturn(Optional.of(account(6, PlatformRole.USER, AccountStatus.DEACTIVATED)));
        AccountBanAppService svc = new AccountBanAppService(bans, accounts, CLOCK);
        assertThatThrownBy(() -> svc.ban(1L, 99L, "r")).extracting(e -> ((BizException) e).getCode())
                .isEqualTo(BizException.NOT_FOUND);
        assertThatThrownBy(() -> svc.ban(1L, 6L, "r")).hasMessageContaining("已注销");
        assertThatThrownBy(() -> svc.unban(5L, " ")).isInstanceOf(DomainException.class);
        verify(bans, never()).delete(any());
    }

    // ---------------------------------------------------------------- 注销

    static final class FakeDeactivations implements AccountDeactivationRepository {
        AccountDeactivation saved;

        @Override
        public void save(AccountDeactivation record) {
            if (record.id() == null) record.identify(11L);
            saved = record;
        }

        @Override
        public Optional<AccountDeactivation> findById(Long id) {
            return Optional.ofNullable(saved).filter(d -> d.id().equals(id));
        }

        @Override
        public Optional<AccountDeactivation> latestByAccount(Long accountId) {
            return Optional.ofNullable(saved).filter(d -> d.accountId().equals(accountId));
        }

        @Override
        public List<AccountDeactivation> page(DeactivationStatus status, LocalDateTime overdueBefore, int offset, int limit) {
            return saved == null ? List.of() : List.of(saved);
        }

        @Override
        public long count(DeactivationStatus status, LocalDateTime overdueBefore) {
            return saved == null ? 0 : 1;
        }
    }

    static AccountErasureStep step(String name, java.util.function.Function<Long, String> body) {
        return new AccountErasureStep() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String erase(Long accountId) {
                return body.apply(accountId);
            }
        };
    }

    @Test
    void 注销_同事务受理_提交后执行步骤_一步失败记FAILED_其余照跑_系统模式() {
        FakeDeactivations repo = new FakeDeactivations();
        AtomicBoolean ranAsSystem = new AtomicBoolean();
        AtomicBoolean flaky = new AtomicBoolean(true);
        AccountDeactivationAppService svc = new AccountDeactivationAppService(repo, List.of(
                step("会失败的步骤", id -> {
                    if (flaky.get()) throw new IllegalStateException("DB 超时");
                    return "ok";
                }),
                step("反馈清除", id -> {
                    ranAsSystem.set(TenantContextHolder.isSystem());
                    return "清除 1 条";
                })), CLOCK);

        svc.onDeactivated(new AccountDeactivated(9L));
        assertThat(repo.saved.status()).isEqualTo(DeactivationStatus.PENDING);
        svc.afterDeactivated(new AccountDeactivated(9L));
        assertThat(repo.saved.status()).isEqualTo(DeactivationStatus.FAILED);
        assertThat(repo.saved.lastError()).contains("DB 超时");
        assertThat(repo.saved.steps()).hasSize(3);
        assertThat(ranAsSystem).as("步骤以系统模式运行（跨家庭）").isTrue();

        flaky.set(false);
        DeactivationView v = svc.retry(11L);
        assertThat(v.status()).isEqualTo("DONE");
        assertThat(v.attempts()).isEqualTo(2);
        assertThat(v.steps()).allMatch(DeactivationView.Step::ok);
        assertThatThrownBy(() -> svc.retry(11L)).isInstanceOf(DomainException.class);
    }

    @Test
    void 注销分页_超期筛选传入分界时间() {
        AccountDeactivationRepository repo = mock(AccountDeactivationRepository.class);
        AccountDeactivationAppService svc = new AccountDeactivationAppService(repo, List.of(), CLOCK);
        svc.page("FAILED", true, 2, 10);
        ArgumentCaptor<LocalDateTime> before = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(repo).page(org.mockito.ArgumentMatchers.eq(DeactivationStatus.FAILED), before.capture(),
                org.mockito.ArgumentMatchers.eq(10), org.mockito.ArgumentMatchers.eq(10));
        assertThat(before.getValue()).isBefore(NOW.minusDays(19));
    }
}
