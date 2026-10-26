package com.gudu.xsd.modules.identity.application;

import com.gudu.xsd.common.BizException;
import com.gudu.xsd.common.domain.DomainEventPublisher;
import com.gudu.xsd.modules.identity.domain.event.AccountMerged;
import com.gudu.xsd.modules.identity.domain.model.Account;
import com.gudu.xsd.modules.identity.domain.model.AccountStatus;
import com.gudu.xsd.modules.identity.domain.model.PhoneNumber;
import com.gudu.xsd.modules.identity.domain.model.PlatformRole;
import com.gudu.xsd.modules.identity.domain.model.WechatIdentity;
import com.gudu.xsd.modules.identity.domain.repository.AccountRepository;
import com.gudu.xsd.modules.identity.domain.service.LoginThrottle;
import com.gudu.xsd.modules.identity.domain.service.PasswordHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** 账号合并编排（E.7）：密码校验 + 限流、存储顺序（先 W 后 P，释放 openid 唯一键）、审计、事件、幂等。 */
class AccountMergeAppServiceTest {

    static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 26, 12, 0);
    final AccountRepository repo = mock(AccountRepository.class);
    final LoginThrottle throttle = mock(LoginThrottle.class);
    final DomainEventPublisher events = mock(DomainEventPublisher.class, CALLS_REAL_METHODS);
    final PasswordHasher hasher = new PasswordHasher() {
        @Override
        public String hash(String raw) {
            return "h:" + raw;
        }

        @Override
        public boolean matches(String raw, String hash) {
            return ("h:" + raw).equals(hash);
        }
    };
    final Clock clock = Clock.fixed(Instant.parse("2026-10-26T04:00:00Z"), ZoneId.of("Asia/Shanghai"));
    AccountMergeAppService svc;
    Account w;
    Account p;

    @BeforeEach
    void setUp() {
        AccountAppService accounts = new AccountAppService(repo, hasher, throttle, events, clock);
        svc = new AccountMergeAppService(accounts, repo, events, clock);
        w = Account.rehydrate(2L, null, WechatIdentity.of("oid-w", null), null, PlatformRole.USER,
                AccountStatus.ACTIVE, "微信用户", null, 7L, null, NOW, null);
        p = Account.rehydrate(1L, PhoneNumber.mobile("13800000001"), null, "h:pw123456", PlatformRole.USER,
                AccountStatus.ACTIVE, "老张", null, 3L, null, NOW, null);
        when(repo.findById(2L)).thenReturn(Optional.of(w));
        when(repo.findByPhone("13800000001")).thenReturn(Optional.of(p));
    }

    @Test
    void 合并成功_先存W再存P_写审计_发AccountMerged() {
        assertThat(svc.mergeWechatIntoPhone(2L, "13800000001", "pw123456")).isEqualTo(1L);
        InOrder order = inOrder(repo);
        order.verify(repo).save(w);
        order.verify(repo).save(p);
        verify(repo).recordMerge(eq(2L), eq(1L), eq(2L), any(), any());
        verify(events).publish(argThat(e -> e instanceof AccountMerged m && m.sourceAccountId() == 2L));
        verify(throttle).reset("13800000001");
    }

    @Test
    void 密码错误_计入限流_不泄露手机号是否存在_两账号不变() {
        assertThatThrownBy(() -> svc.mergeWechatIntoPhone(2L, "13800000001", "bad"))
                .isInstanceOf(BizException.class).hasMessage(AccountAppService.BAD_CREDENTIALS);
        assertThatThrownBy(() -> svc.mergeWechatIntoPhone(2L, "13900000000", "bad"))
                .hasMessage(AccountAppService.BAD_CREDENTIALS);
        verify(throttle, times(2)).recordFailure(any());
        verify(repo, never()).save(any());
        assertThat(w.status()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void 已锁定_429() {
        when(throttle.isLocked("13800000001")).thenReturn(true);
        assertThatThrownBy(() -> svc.mergeWechatIntoPhone(2L, "13800000001", "pw123456"))
                .isInstanceOfSatisfying(BizException.class, e -> assertThat(e.getCode()).isEqualTo(429));
    }

    @Test
    void 幂等_W已并入P_再次提交直接返回P_不重复写() {
        svc.mergeWechatIntoPhone(2L, "13800000001", "pw123456");
        clearInvocations(repo, events);
        assertThat(svc.mergeWechatIntoPhone(2L, "13800000001", "pw123456")).isEqualTo(1L);
        verify(repo, never()).save(any());
        verify(events, never()).publish(any());
    }

    @Test
    void 当前账号不是纯微信账号_拒绝() {
        when(repo.findById(1L)).thenReturn(Optional.of(p));
        assertThatThrownBy(() -> svc.mergeWechatIntoPhone(1L, "13800000001", "pw123456"))
                .hasMessageContaining("不是微信账号");
    }
}
