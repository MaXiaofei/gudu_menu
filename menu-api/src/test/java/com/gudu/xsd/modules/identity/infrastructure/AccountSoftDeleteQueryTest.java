package com.gudu.xsd.modules.identity.infrastructure;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.gudu.xsd.modules.identity.domain.model.Account;
import com.gudu.xsd.modules.identity.domain.model.AccountStatus;
import com.gudu.xsd.modules.identity.domain.model.PlatformRole;
import com.gudu.xsd.modules.identity.domain.model.WechatIdentity;
import com.gudu.xsd.modules.identity.infrastructure.mapper.AccountMapper;
import com.gudu.xsd.modules.identity.infrastructure.mapper.AccountMergeLogMapper;
import com.gudu.xsd.modules.identity.infrastructure.po.AccountPO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 被合并账号软删除后：仓储批量查询 / 后台账号目录默认排除，deleted_at 随账号落库。 */
class AccountSoftDeleteQueryTest {

    @org.junit.jupiter.api.BeforeEach
    void install() {
        com.gudu.xsd.common.crypto.FieldCrypto.install(com.gudu.xsd.common.crypto.FieldCipherTest.v1());
    }

    @org.junit.jupiter.api.AfterEach
    void uninstall() {
        com.gudu.xsd.common.crypto.FieldCrypto.uninstall();
    }

    static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 2, 12, 0);

    @Test
    void toPO_带上deletedAt_toDomain_还原() {
        Account w = Account.rehydrate(2L, null, WechatIdentity.of("o", null), null, PlatformRole.USER,
                AccountStatus.MERGED, null, null, null, 1L, NOW, NOW, NOW.plusDays(1));
        AccountPO po = MybatisAccountRepository.toPO(w);
        assertThat(po.getDeletedAt()).isEqualTo(NOW.plusDays(1));
        assertThat(MybatisAccountRepository.toDomain(po).isDeleted()).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void 批量取账号与昵称_排除已软删除() {
        AccountMapper mapper = mock(AccountMapper.class);
        when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        MybatisAccountRepository repo = new MybatisAccountRepository(mapper, mock(AccountMergeLogMapper.class));

        repo.findAllByIds(List.of(1L, 2L));
        repo.nicknames(List.of(1L, 2L));

        ArgumentCaptor<Wrapper<AccountPO>> cap = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper, org.mockito.Mockito.times(2)).selectList(cap.capture());
        for (Wrapper<AccountPO> w : cap.getAllValues()) {
            assertThat(w.getSqlSegment()).contains("deleted_at IS NULL");
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void 按手机号_按openid查_排除已软删除() {
        AccountMapper mapper = mock(AccountMapper.class);
        when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        MybatisAccountRepository repo = new MybatisAccountRepository(mapper, mock(AccountMergeLogMapper.class));
        repo.findByPhone("13800000001");
        repo.findByOpenid("oid");

        ArgumentCaptor<Wrapper<AccountPO>> cap = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).selectList(cap.capture());
        assertThat(cap.getValue().getSqlSegment()).contains("deleted_at IS NULL");
        ArgumentCaptor<Wrapper<AccountPO>> cap2 = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).selectOne(cap2.capture());
        assertThat(cap2.getValue().getSqlSegment()).contains("deleted_at IS NULL");
    }

    @Test
    void 按id直取仍可取到软删除账号_供旧token和合并幂等判断() {
        AccountMapper mapper = mock(AccountMapper.class);
        AccountPO po = new AccountPO();
        po.setId(2L);
        po.setStatus("MERGED");
        po.setMergedInto(1L);
        po.setDeletedAt(NOW);
        when(mapper.selectById(2L)).thenReturn(po);
        MybatisAccountRepository repo = new MybatisAccountRepository(mapper, mock(AccountMergeLogMapper.class));
        Account a = repo.findById(2L).orElseThrow();
        assertThat(a.isDeleted()).isTrue();
        assertThat(a.mergedInto()).isEqualTo(1L);
    }
}
