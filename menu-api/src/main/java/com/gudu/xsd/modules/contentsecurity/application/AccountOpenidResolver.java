package com.gudu.xsd.modules.contentsecurity.application;

import com.gudu.xsd.modules.contentsecurity.domain.port.OpenidResolver;
import com.gudu.xsd.modules.identity.application.AccountQueryApi;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * openid 解析：multi-tenant-family 起登录身份在 account 表（account.wx_openid），内容安全传入的是账号 id
 * （原实现按 member.id 查 member.openid，账号与档案分离后查不到）。走 identity 的开放查询（上下文 API）；
 * 跨上下文调用只能落在 application 层，所以适配器放这里而不是 infrastructure。
 */
@Component
public class AccountOpenidResolver implements OpenidResolver {

    private final AccountQueryApi accountQuery;

    public AccountOpenidResolver(AccountQueryApi accountQuery) {
        this.accountQuery = accountQuery;
    }

    @Override
    public Optional<String> openidOf(Long accountId) {
        return accountQuery.openidOf(accountId).filter(s -> !s.isBlank());
    }
}
