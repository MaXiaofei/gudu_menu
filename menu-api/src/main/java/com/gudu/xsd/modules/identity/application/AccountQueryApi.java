package com.gudu.xsd.modules.identity.application;

import com.gudu.xsd.modules.identity.application.dto.AccountView;
import com.gudu.xsd.modules.identity.domain.model.Account;
import com.gudu.xsd.modules.identity.domain.model.AccountStatus;
import com.gudu.xsd.modules.identity.domain.model.PlatformRole;
import com.gudu.xsd.modules.identity.domain.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/** identity 上下文对外查询（开放主机服务）：平台角色判定、昵称批量查询。 */
@Service
@RequiredArgsConstructor
public class AccountQueryApi {

    private final AccountRepository repository;

    public Optional<AccountView> find(Long accountId) {
        return repository.findById(accountId).map(AccountQueryApi::view);
    }

    /** 平台超管（原 member.is_admin=1）。非 ACTIVE 账号一律 false。 */
    public boolean isSuperAdmin(Long accountId) {
        return hasRole(accountId, PlatformRole.SUPER_ADMIN);
    }

    /** 平台运营及以上（维护公共菜谱库）。 */
    public boolean isOperator(Long accountId) {
        return hasRole(accountId, PlatformRole.OPERATOR);
    }

    /** 微信 openid（内容安全调用微信接口用）；未绑定微信 / 非 ACTIVE 返回 empty。 */
    public Optional<String> openidOf(Long accountId) {
        if (accountId == null) return Optional.empty();
        return repository.findById(accountId)
                .filter(a -> a.status() == AccountStatus.ACTIVE && a.wechat() != null)
                .map(a -> a.wechat().openid());
    }

    /** 平台角色（不存在返回 empty）。 */
    public Optional<PlatformRole> platformRoleOf(Long accountId) {
        if (accountId == null) return Optional.empty();
        return repository.findById(accountId).map(Account::platformRole);
    }

    public Map<Long, String> nicknames(Collection<Long> accountIds) {
        return repository.nicknames(accountIds);
    }

    /** 批量账号视图（社区作者名 / 头像等跨家庭展示）。 */
    public Map<Long, AccountView> views(Collection<Long> accountIds) {
        Map<Long, AccountView> out = new java.util.HashMap<>();
        for (Account a : repository.findAllByIds(accountIds)) out.put(a.id(), view(a));
        return out;
    }

    private boolean hasRole(Long accountId, PlatformRole role) {
        if (accountId == null) return false;
        return repository.findById(accountId)
                .filter(a -> a.status() == AccountStatus.ACTIVE)
                .map(a -> a.platformRole().atLeast(role))
                .orElse(false);
    }

    static AccountView view(Account a) {
        return new AccountView(a.id(), a.nickname(), a.avatarUrl(), a.phone() == null ? null : a.phone().masked(),
                a.wechat() != null, a.passwordHash() != null, a.platformRole().name(), a.status().name());
    }
}
