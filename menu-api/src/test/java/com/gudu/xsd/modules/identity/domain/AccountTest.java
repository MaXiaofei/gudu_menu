package com.gudu.xsd.modules.identity.domain;

import com.gudu.xsd.common.domain.DomainException;
import com.gudu.xsd.modules.identity.domain.event.AccountDeactivated;
import com.gudu.xsd.modules.identity.domain.event.AccountMerged;
import com.gudu.xsd.modules.identity.domain.event.AccountRegistered;
import com.gudu.xsd.modules.identity.domain.model.Account;
import com.gudu.xsd.modules.identity.domain.model.AccountStatus;
import com.gudu.xsd.modules.identity.domain.model.PhoneNumber;
import com.gudu.xsd.modules.identity.domain.model.PlatformRole;
import com.gudu.xsd.modules.identity.domain.model.WechatIdentity;
import com.gudu.xsd.modules.identity.domain.service.PasswordHasher;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Account 聚合（multi-tenant-family A.3 / E.6 / E.7）：注册、密码、注销匿名化、合并 absorb（冲突 / 幂等 / 已合并）。 */
class AccountTest {

    static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 26, 12, 0);

    /** 明文前缀 h: 的假哈希（领域测试不依赖 BCrypt）。 */
    static final PasswordHasher HASHER = new PasswordHasher() {
        @Override
        public String hash(String raw) {
            return "h:" + raw;
        }

        @Override
        public boolean matches(String raw, String hash) {
            return ("h:" + raw).equals(hash);
        }
    };

    static Account phoneAccount(long id, String nickname, String openid) {
        return Account.rehydrate(id, PhoneNumber.mobile("13800000001"),
                openid == null ? null : WechatIdentity.of(openid, null), "h:pw123456", PlatformRole.USER,
                AccountStatus.ACTIVE, nickname, null, null, null, NOW, null);
    }

    static Account wechatAccount(long id, String nickname, String avatar) {
        return Account.rehydrate(id, null, WechatIdentity.of("oid-w", null), null, PlatformRole.USER,
                AccountStatus.ACTIVE, nickname, avatar, null, null, NOW, null);
    }

    @Test
    void 微信注册_默认昵称_回填id后发AccountRegistered带邀请码() {
        Account a = Account.registerByWechat(WechatIdentity.of("oid-1", null), "ABCDEFGH", NOW);
        assertThat(a.nickname()).isEqualTo(Account.DEFAULT_WECHAT_NICKNAME);
        assertThat(a.isWechatOnly()).isTrue();
        assertThat(a.peekDomainEvents()).isEmpty();
        a.identify(5L);
        AccountRegistered e = (AccountRegistered) a.pullDomainEvents().get(0);
        assertThat(e.accountId()).isEqualTo(5L);
        assertThat(e.inviteCode()).isEqualTo("ABCDEFGH");
        assertThat(e.autoCreateFamily()).isTrue();
    }

    @Test
    void 手机号注册_校验手机号与密码长度_可指定不自动建家() {
        assertThatThrownBy(() -> PhoneNumber.mobile("12345")).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> Account.registerByPhone(PhoneNumber.mobile("13800000002"), "123", null, true, HASHER, NOW))
                .hasMessageContaining("密码");
        Account a = Account.registerByPhone(PhoneNumber.mobile("13800000002"), "pw123456", null, false, HASHER, NOW);
        assertThat(a.nickname()).isEqualTo("用户0002");
        assertThat(a.passwordMatches("pw123456", HASHER)).isTrue();
        a.identify(6L);
        assertThat(((AccountRegistered) a.pullDomainEvents().get(0)).autoCreateFamily()).isFalse();
    }

    @Test
    void 改密码_须校验旧密码() {
        Account a = phoneAccount(1L, "老张", null);
        assertThatThrownBy(() -> a.changePassword("wrong", "newpw123", HASHER)).hasMessageContaining("原密码");
        a.changePassword("pw123456", "newpw123", HASHER);
        assertThat(a.passwordMatches("newpw123", HASHER)).isTrue();
    }

    @Test
    void 注销_匿名化手机号微信昵称头像_状态DEACTIVATED_不能再登录() {
        Account a = phoneAccount(1L, "老张", "oid-p");
        a.deactivate(NOW);
        assertThat(a.status()).isEqualTo(AccountStatus.DEACTIVATED);
        assertThat(a.phone()).isNull();
        assertThat(a.wechat()).isNull();
        assertThat(a.nickname()).isNull();
        assertThat(a.passwordHash()).isNull();
        assertThat(a.pullDomainEvents()).singleElement().isInstanceOf(AccountDeactivated.class);
        assertThatThrownBy(a::ensureCanLogin).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> a.deactivate(NOW)).isInstanceOf(DomainException.class);
    }

    @Test
    void 合并_微信身份移到手机号账号_W标记MERGED_发AccountMerged() {
        Account p = phoneAccount(1L, "老张", null);
        Account w = wechatAccount(2L, "张三", "/uploads/w.png");
        assertThat(p.absorb(w, NOW)).isTrue();
        assertThat(p.wechat().openid()).isEqualTo("oid-w");
        assertThat(p.nickname()).as("P 改过昵称：保留 P 的").isEqualTo("老张");
        assertThat(p.avatarUrl()).as("P 无头像：取 W 的").isEqualTo("/uploads/w.png");
        assertThat(p.platformRole()).isEqualTo(PlatformRole.USER);
        assertThat(w.status()).isEqualTo(AccountStatus.MERGED);
        assertThat(w.mergedInto()).isEqualTo(1L);
        assertThat(w.wechat()).isNull();
        AccountMerged e = (AccountMerged) p.pullDomainEvents().get(0);
        assertThat(e.sourceAccountId()).isEqualTo(2L);
        assertThat(e.targetAccountId()).isEqualTo(1L);
    }

    @Test
    void 合并_P昵称仍是默认值时取W的() {
        Account p = phoneAccount(1L, Account.DEFAULT_WECHAT_NICKNAME, null);
        p.absorb(wechatAccount(2L, "张三", null), NOW);
        assertThat(p.nickname()).isEqualTo("张三");
    }

    @Test
    void 合并_P已绑定其他微信_冲突_两个账号都不变() {
        Account p = phoneAccount(1L, "老张", "oid-other");
        Account w = wechatAccount(2L, "张三", null);
        assertThatThrownBy(() -> p.absorb(w, NOW)).isInstanceOf(AccountWechatConflictException.class);
        assertThat(p.wechat().openid()).isEqualTo("oid-other");
        assertThat(w.status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(w.wechat()).isNotNull();
        assertThat(p.peekDomainEvents()).isEmpty();
    }

    @Test
    void 合并_幂等_已并入本账号再次合并不重复发事件() {
        Account p = phoneAccount(1L, "老张", null);
        Account w = wechatAccount(2L, "张三", null);
        p.absorb(w, NOW);
        p.pullDomainEvents();
        assertThat(p.absorb(w, NOW)).isFalse();
        assertThat(p.peekDomainEvents()).isEmpty();
    }

    @Test
    void 合并_来源已注销或有手机号_或与自己合并_拒绝() {
        Account p = phoneAccount(1L, "老张", null);
        Account dead = wechatAccount(2L, "张三", null);
        dead.deactivate(NOW);
        assertThatThrownBy(() -> p.absorb(dead, NOW)).isInstanceOf(DomainException.class);
        Account other = phoneAccount(3L, "李四", null);
        assertThatThrownBy(() -> p.absorb(other, NOW)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> p.absorb(p, NOW)).hasMessageContaining("自己");
    }

    @Test
    void 平台角色等级() {
        assertThat(PlatformRole.SUPER_ADMIN.atLeast(PlatformRole.OPERATOR)).isTrue();
        assertThat(PlatformRole.USER.atLeast(PlatformRole.OPERATOR)).isFalse();
        assertThat(PlatformRole.of("bogus")).isEqualTo(PlatformRole.USER);
        assertThat(PhoneNumber.mobile("13800000001").masked()).isEqualTo("138****0001");
        assertThat(PhoneNumber.rehydrate("admin").masked()).isNull();
    }
}
