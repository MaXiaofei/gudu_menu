package com.gudu.xsd.modules.identity.infrastructure;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.gudu.xsd.common.crypto.FieldCipherTest;
import com.gudu.xsd.common.crypto.FieldCrypto;
import com.gudu.xsd.common.crypto.EncryptedStringTypeHandler;
import com.gudu.xsd.modules.identity.domain.model.Account;
import com.gudu.xsd.modules.identity.domain.model.PhoneNumber;
import com.gudu.xsd.modules.identity.infrastructure.mapper.AccountMapper;
import com.gudu.xsd.modules.identity.infrastructure.mapper.AccountMergeLogMapper;
import com.gudu.xsd.modules.identity.infrastructure.po.AccountPO;
import com.baomidou.mybatisplus.annotation.TableField;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** B.3：手机号加密落库（TypeHandler）+ 盲索引写入与按盲索引查询（兼容未迁移明文）。 */
class AccountPhoneEncryptionTest {

    @BeforeEach
    void install() {
        FieldCrypto.install(FieldCipherTest.v1());
    }

    @AfterEach
    void uninstall() {
        FieldCrypto.uninstall();
    }

    @Test
    void PO手机号列声明加密TypeHandler_写入时带盲索引() throws Exception {
        TableField tf = AccountPO.class.getDeclaredField("phone").getAnnotation(TableField.class);
        assertThat(tf.typeHandler()).isEqualTo(EncryptedStringTypeHandler.class);
        Account a = Account.rehydrate(5L, PhoneNumber.mobile("13800000001"), null, "h",
                com.gudu.xsd.modules.identity.domain.model.PlatformRole.USER,
                com.gudu.xsd.modules.identity.domain.model.AccountStatus.ACTIVE, "张", null, null, null,
                LocalDateTime.now(), null);
        AccountPO po = MybatisAccountRepository.toPO(a);
        assertThat(po.getPhone()).isEqualTo("13800000001"); // 明文交给 TypeHandler 加密
        assertThat(po.getPhoneHash()).isEqualTo(FieldCrypto.blindIndex("13800000001"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void 按手机号查_走盲索引_兼容未迁移明文行() {
        AccountMapper mapper = mock(AccountMapper.class);
        when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        new MybatisAccountRepository(mapper, mock(AccountMergeLogMapper.class)).findByPhone(" 13800000001 ");
        ArgumentCaptor<Wrapper<AccountPO>> cap = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).selectList(cap.capture());
        String sql = cap.getValue().getSqlSegment();
        assertThat(sql).contains("phone_hash =").contains("phone_hash IS NULL").contains("phone =");
        assertThat(((com.baomidou.mybatisplus.core.conditions.AbstractWrapper<?, ?, ?>) cap.getValue())
                .getParamNameValuePairs().values())
                .contains(FieldCrypto.blindIndex("13800000001"), "13800000001");
    }
}
