package com.gudu.xsd.modules.identity.application;

import com.gudu.xsd.common.BizException;
import com.gudu.xsd.common.domain.DomainEventPublisher;
import com.gudu.xsd.common.domain.DomainException;
import com.gudu.xsd.common.domain.UploadedImageUrl;
import com.gudu.xsd.modules.identity.application.dto.AccountView;
import com.gudu.xsd.modules.identity.domain.model.Account;
import com.gudu.xsd.modules.identity.domain.model.PhoneNumber;
import com.gudu.xsd.modules.identity.domain.model.WechatIdentity;
import com.gudu.xsd.modules.identity.domain.repository.AccountRepository;
import com.gudu.xsd.modules.identity.domain.service.LoginThrottle;
import com.gudu.xsd.modules.identity.domain.service.PasswordHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * 账号用例：登录认证、微信静默注册、资料、改密、注销、超管代建手机号账号。
 * 登录态（Sa-Token）由接口层处理，本服务只返回已认证的账号。
 */
@Service
@RequiredArgsConstructor
public class AccountAppService {

    static final String BAD_CREDENTIALS = "用户名或密码错误";

    private final AccountRepository repository;
    private final PasswordHasher hasher;
    private final LoginThrottle throttle;
    private final DomainEventPublisher events;
    private final Clock clock;

    /** 手机号（登录名）+ 密码认证；失败计入限流，不区分「账号不存在」与「密码错」。 */
    public AccountView authenticate(String loginName, String rawPassword) {
        return AccountQueryApi.view(verifyCredentials(loginName, rawPassword));
    }

    /** 校验凭证并返回账号聚合（供合并等用例复用）。 */
    Account verifyCredentials(String loginName, String rawPassword) {
        String key = loginName == null ? "" : loginName.trim();
        if (throttle.isLocked(key)) throw BizException.tooManyRequests("密码错误次数过多，请 15 分钟后再试");
        Account a = repository.findByPhone(key).orElse(null);
        if (a == null || !a.passwordMatches(rawPassword, hasher)) {
            throttle.recordFailure(key);
            throw new BizException(BAD_CREDENTIALS);
        }
        try {
            a.ensureCanLogin();
        } catch (DomainException e) {
            throttle.recordFailure(key);
            throw new BizException(BAD_CREDENTIALS);
        }
        throttle.reset(key);
        return a;
    }

    /**
     * 微信静默登录：同一 openid 永远同一账号；首次出现自动注册并发 AccountRegistered
     * （family 上下文据此建「我的家」或凭 inviteCode 加入邀请家庭）。
     * 已并入手机号账号的 openid 已迁到手机号账号上，这里自然登录到手机号账号。
     */
    @Transactional(rollbackFor = Exception.class)
    public AccountView loginOrRegisterByWechat(String openid, String unionid, String inviteCode) {
        Account a = repository.findByOpenid(openid).orElse(null);
        if (a == null) {
            a = Account.registerByWechat(WechatIdentity.of(openid, unionid), blankToNull(inviteCode),
                    LocalDateTime.now(clock));
            repository.save(a);
            events.publishFrom(a);
        } else {
            try {
                a.ensureCanLogin();
            } catch (DomainException e) {
                throw new BizException("账号不可用");
            }
        }
        return AccountQueryApi.view(a);
    }

    /**
     * 平台超管代建手机号账号（V1 无短信，手机号账号只能超管建）。发 AccountRegistered。
     *
     * @param autoCreateFamily false = 调用方随后把账号加入指定家庭（不自动建「我的家」）
     */
    @Transactional(rollbackFor = Exception.class)
    public Long registerByPhone(String phone, String rawPassword, String nickname, boolean autoCreateFamily) {
        PhoneNumber p = PhoneNumber.mobile(phone);
        if (repository.findByPhone(p.value()).isPresent()) throw new BizException("该手机号已注册");
        Account a = Account.registerByPhone(p, rawPassword, nickname, autoCreateFamily, hasher,
                LocalDateTime.now(clock));
        repository.save(a);
        events.publishFrom(a);
        return a.id();
    }

    public AccountView me(Long accountId) {
        return AccountQueryApi.view(load(accountId));
    }

    /** 改昵称 / 头像；null 表示不改，头像空串表示清除。 */
    @Transactional(rollbackFor = Exception.class)
    public AccountView updateProfile(Long accountId, String nickname, String avatarUrl) {
        Account a = load(accountId);
        if (nickname != null) a.rename(nickname);
        if (avatarUrl != null) {
            a.changeAvatar(avatarUrl.isBlank() ? null : new UploadedImageUrl(avatarUrl.trim()).value());
        }
        repository.save(a);
        return AccountQueryApi.view(a);
    }

    @Transactional(rollbackFor = Exception.class)
    public void changePassword(Long accountId, String oldRaw, String newRaw) {
        Account a = load(accountId);
        a.changePassword(oldRaw, newRaw, hasher);
        repository.save(a);
    }

    public Long lastFamilyId(Long accountId) {
        return repository.findById(accountId).map(Account::lastFamilyId).orElse(null);
    }

    /** 记住上次使用的家庭（下次登录默认进入）。 */
    @Transactional(rollbackFor = Exception.class)
    public void rememberFamily(Long accountId, Long familyId) {
        repository.findById(accountId).ifPresent(a -> {
            if (familyId != null && familyId.equals(a.lastFamilyId())) return;
            a.rememberFamily(familyId);
            repository.save(a);
        });
    }

    /**
     * 注销：匿名化账号并发 AccountDeactivated。family 上下文同事务消费——
     * 是有其他成员的家庭的 OWNER 时抛异常整体回滚（须先转让或解散），否则退出全部家庭。
     */
    @Transactional(rollbackFor = Exception.class)
    public void deactivate(Long accountId) {
        Account a = load(accountId);
        a.deactivate(LocalDateTime.now(clock));
        repository.save(a);
        events.publishFrom(a);
    }

    Account load(Long accountId) {
        return repository.findById(accountId).orElseThrow(() -> new BizException("账号不存在"));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
