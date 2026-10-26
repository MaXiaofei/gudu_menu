package com.gudu.xsd.modules.identity.infrastructure;

import com.gudu.xsd.modules.identity.domain.model.AccountBan;
import com.gudu.xsd.modules.identity.domain.repository.AccountBanRepository;
import com.gudu.xsd.modules.identity.infrastructure.mapper.AccountBanMapper;
import com.gudu.xsd.modules.identity.infrastructure.po.AccountBanPO;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class MybatisAccountBanRepository implements AccountBanRepository {

    private final AccountBanMapper mapper;

    public MybatisAccountBanRepository(AccountBanMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<AccountBan> find(Long accountId) {
        if (accountId == null) return Optional.empty();
        AccountBanPO po = mapper.selectById(accountId);
        return po == null ? Optional.empty()
                : Optional.of(AccountBan.rehydrate(po.getAccountId(), po.getReason(), po.getBannedBy(), po.getBannedAt()));
    }

    @Override
    public boolean exists(Long accountId) {
        return accountId != null && mapper.selectById(accountId) != null;
    }

    @Override
    public void save(AccountBan ban) {
        AccountBanPO po = new AccountBanPO();
        po.setAccountId(ban.accountId());
        po.setReason(ban.reason());
        po.setBannedBy(ban.bannedBy());
        po.setBannedAt(ban.bannedAt());
        if (mapper.selectById(ban.accountId()) == null) {
            mapper.insert(po);
        } else {
            mapper.updateById(po);
        }
    }

    @Override
    public boolean delete(Long accountId) {
        return accountId != null && mapper.deleteById(accountId) > 0;
    }
}
