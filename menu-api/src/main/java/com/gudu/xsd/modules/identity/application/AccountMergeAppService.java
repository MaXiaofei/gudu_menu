package com.gudu.xsd.modules.identity.application;

import com.gudu.xsd.common.BizException;
import com.gudu.xsd.common.domain.DomainEventPublisher;
import com.gudu.xsd.modules.identity.domain.model.Account;
import com.gudu.xsd.modules.identity.domain.model.AccountStatus;
import com.gudu.xsd.modules.identity.domain.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * 微信账号并入手机号账号（design §3.1「账号合并流程」，单事务、幂等）。
 *
 * <ol>
 *   <li>当前登录 = 微信账号 W；校验手机号 + 密码得到 P（失败计入登录限流，不泄露手机号是否存在）；</li>
 *   <li>{@code P.absorb(W)}：微信身份移到 P，W=MERGED；先存 W（释放 openid 唯一键）再存 P；</li>
 *   <li>发 AccountMerged：family / agent / feedback / vision / mealplan / community 同事务把 W 的数据改指 P；</li>
 *   <li>写 account_merge_log 审计。token 失效与重签由接口层处理。</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class AccountMergeAppService {

    private final AccountAppService accounts;
    private final AccountRepository repository;
    private final DomainEventPublisher events;
    private final Clock clock;

    /** @return 合并后的账号（P）id */
    @Transactional(rollbackFor = Exception.class)
    public Long mergeWechatIntoPhone(Long currentAccountId, String phone, String rawPassword) {
        Account w = accounts.load(currentAccountId);
        Account p = accounts.verifyCredentials(phone, rawPassword);
        if (w.status() == AccountStatus.MERGED && p.id().equals(w.mergedInto())) return p.id(); // 幂等：重复提交
        if (!w.isWechatOnly()) throw new BizException("当前账号不是微信账号，无需合并");
        if (!p.absorb(w, LocalDateTime.now(clock))) return p.id();
        repository.save(w);
        repository.save(p);
        repository.recordMerge(w.id(), p.id(), currentAccountId, "wechat->phone", LocalDateTime.now(clock));
        events.publishFrom(p);
        return p.id();
    }
}
