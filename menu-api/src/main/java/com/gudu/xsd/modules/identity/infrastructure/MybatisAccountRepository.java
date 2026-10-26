package com.gudu.xsd.modules.identity.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gudu.xsd.common.crypto.FieldCrypto;
import com.gudu.xsd.modules.identity.domain.model.Account;
import com.gudu.xsd.modules.identity.domain.model.AccountStatus;
import com.gudu.xsd.modules.identity.domain.model.PhoneNumber;
import com.gudu.xsd.modules.identity.domain.model.PlatformRole;
import com.gudu.xsd.modules.identity.domain.model.WechatIdentity;
import com.gudu.xsd.modules.identity.domain.repository.AccountRepository;
import com.gudu.xsd.modules.identity.infrastructure.mapper.AccountMapper;
import com.gudu.xsd.modules.identity.infrastructure.mapper.AccountMergeLogMapper;
import com.gudu.xsd.modules.identity.infrastructure.po.AccountMergeLogPO;
import com.gudu.xsd.modules.identity.infrastructure.po.AccountPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class MybatisAccountRepository implements AccountRepository {

    private final AccountMapper mapper;
    private final AccountMergeLogMapper mergeLogMapper;

    @Override
    public Optional<Account> findById(Long id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(mapper.selectById(id)).map(MybatisAccountRepository::toDomain);
    }

    @Override
    public Optional<Account> findByPhone(String phone) {
        if (phone == null || phone.isBlank()) return Optional.empty();
        String p = phone.trim();
        String hash = FieldCrypto.blindIndex(p);
        // 手机号加密存储（B.3）：按盲索引查；存量未迁移的明文行（phone_hash 为空）兜底按明文匹配
        return mapper.selectList(new QueryWrapper<AccountPO>()
                        .and(w -> w.eq("phone_hash", hash).or(x -> x.isNull("phone_hash").eq("phone", p)))
                        .orderByAsc("id"))
                .stream().findFirst().map(MybatisAccountRepository::toDomain);
    }

    @Override
    public Optional<Account> findByOpenid(String openid) {
        if (openid == null || openid.isBlank()) return Optional.empty();
        return Optional.ofNullable(mapper.selectOne(new QueryWrapper<AccountPO>().eq("wx_openid", openid)))
                .map(MybatisAccountRepository::toDomain);
    }

    @Override
    public void save(Account a) {
        AccountPO po = toPO(a);
        if (a.id() == null) {
            mapper.insert(po);
            a.identify(po.getId());
        } else {
            mapper.updateById(po);
        }
    }

    @Override
    public java.util.List<Account> findAllByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) return java.util.List.of();
        return mapper.selectBatchIds(ids).stream().map(MybatisAccountRepository::toDomain).toList();
    }

    @Override
    public Map<Long, String> nicknames(Collection<Long> ids) {
        Map<Long, String> out = new HashMap<>();
        if (ids == null || ids.isEmpty()) return out;
        for (AccountPO po : mapper.selectList(new QueryWrapper<AccountPO>().select("id", "nickname").in("id", ids))) {
            out.put(po.getId(), po.getNickname() == null ? "" : po.getNickname());
        }
        return out;
    }

    @Override
    public void recordMerge(Long sourceId, Long targetId, Long operatorId, String detail, LocalDateTime at) {
        AccountMergeLogPO po = new AccountMergeLogPO();
        po.setSourceId(sourceId);
        po.setTargetId(targetId);
        po.setOperatorId(operatorId);
        po.setDetail(detail);
        po.setCreatedAt(at);
        mergeLogMapper.insert(po);
    }

    static Account toDomain(AccountPO po) {
        WechatIdentity wx = po.getWxOpenid() == null ? null : WechatIdentity.of(po.getWxOpenid(), po.getWxUnionid());
        return Account.rehydrate(po.getId(), PhoneNumber.rehydrate(po.getPhone()), wx, po.getPasswordHash(),
                PlatformRole.of(po.getPlatformRole()), AccountStatus.of(po.getStatus()), po.getNickname(),
                po.getAvatarUrl(), po.getLastFamilyId(), po.getMergedInto(), po.getCreatedAt(), po.getDeactivatedAt());
    }

    static AccountPO toPO(Account a) {
        AccountPO po = new AccountPO();
        po.setId(a.id());
        po.setPhone(a.phone() == null ? null : a.phone().value());
        po.setPhoneHash(a.phone() == null ? null : FieldCrypto.blindIndex(a.phone().value()));
        po.setWxOpenid(a.wechat() == null ? null : a.wechat().openid());
        po.setWxUnionid(a.wechat() == null ? null : a.wechat().unionid());
        po.setPasswordHash(a.passwordHash());
        po.setNickname(a.nickname());
        po.setAvatarUrl(a.avatarUrl());
        po.setPlatformRole(a.platformRole().name());
        po.setStatus(a.status().name());
        po.setLastFamilyId(a.lastFamilyId());
        po.setMergedInto(a.mergedInto());
        po.setCreatedAt(a.createdAt());
        po.setDeactivatedAt(a.deactivatedAt());
        return po;
    }
}
