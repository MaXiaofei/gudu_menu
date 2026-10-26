package com.gudu.xsd.modules.identity.application;

import com.gudu.xsd.common.BizException;
import com.gudu.xsd.modules.identity.domain.model.Account;
import com.gudu.xsd.modules.identity.domain.model.AccountBan;
import com.gudu.xsd.modules.identity.domain.model.AccountStatus;
import com.gudu.xsd.modules.identity.domain.repository.AccountBanRepository;
import com.gudu.xsd.modules.identity.domain.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 账号封禁（mini-release-compliance F.1）：封禁后登录被拒、写接口被拒（统一 423 ACCOUNT_BANNED）。
 * 下线登录态由接口层处理（Sa-Token）。操作留痕由运营后台应用服务写操作日志。
 */
@Service
@RequiredArgsConstructor
public class AccountBanAppService {

    private final AccountBanRepository bans;
    private final AccountRepository accounts;
    private final Clock clock;

    /** 封禁视图。 */
    public record BanView(Long accountId, String reason, Long bannedBy, LocalDateTime bannedAt) {
    }

    @Transactional(rollbackFor = Exception.class)
    public BanView ban(Long operatorId, Long accountId, String reason) {
        Account target = accounts.findById(accountId).orElseThrow(() -> BizException.notFound("账号不存在"));
        if (target.status() != AccountStatus.ACTIVE) throw new BizException("账号已注销或已合并，无需封禁");
        Account operator = accounts.findById(operatorId).orElseThrow(() -> BizException.forbidden("无权限"));
        AccountBan ban = AccountBan.impose(accountId, target.platformRole(), operatorId, operator.platformRole(),
                reason, LocalDateTime.now(clock));
        bans.save(ban);
        return view(ban);
    }

    /** @return 是否确实解除了封禁（原本未封禁返回 false） */
    @Transactional(rollbackFor = Exception.class)
    public boolean unban(Long accountId, String reason) {
        AccountBan.checkReason(reason);
        return bans.delete(accountId);
    }

    public Optional<BanView> banOf(Long accountId) {
        return bans.find(accountId).map(AccountBanAppService::view);
    }

    public boolean isBanned(Long accountId) {
        return bans.exists(accountId);
    }

    /** 登录 / 写接口前置：已封禁抛 423 ACCOUNT_BANNED（附原因）。 */
    public void assertNotBanned(Long accountId) {
        bans.find(accountId).ifPresent(b -> {
            throw BizException.accountBanned(b.reason());
        });
    }

    private static BanView view(AccountBan b) {
        return new BanView(b.accountId(), b.reason(), b.bannedBy(), b.bannedAt());
    }
}
