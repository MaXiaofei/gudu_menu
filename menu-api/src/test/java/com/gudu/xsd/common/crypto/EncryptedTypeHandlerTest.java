package com.gudu.xsd.common.crypto;

import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EncryptedTypeHandlerTest {

    @BeforeEach
    void install() {
        FieldCrypto.install(FieldCipherTest.v1());
    }

    @AfterEach
    void uninstall() {
        FieldCrypto.uninstall();
    }

    @Test
    void 字符串_写入为密文_读出解密_存量明文兼容() throws Exception {
        EncryptedStringTypeHandler h = new EncryptedStringTypeHandler();
        PreparedStatement ps = mock(PreparedStatement.class);
        h.setNonNullParameter(ps, 1, "13800000001", JdbcType.VARCHAR);
        ArgumentCaptor<String> cap = ArgumentCaptor.forClass(String.class);
        verify(ps).setString(anyInt(), cap.capture());
        assertThat(cap.getValue()).startsWith("ENC:v1:").doesNotContain("13800000001");

        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("phone")).thenReturn(cap.getValue());
        assertThat(h.getNullableResult(rs, "phone")).isEqualTo("13800000001");
        when(rs.getString("phone")).thenReturn("13900000088");
        assertThat(h.getNullableResult(rs, "phone")).isEqualTo("13900000088");
        when(rs.getString(2)).thenReturn(null);
        assertThat(h.getNullableResult(rs, 2)).isNull();
    }

    @Test
    void 健康档案Map_密文往返_明文JSON兼容_空串为null() throws Exception {
        EncryptedJsonMapTypeHandler h = new EncryptedJsonMapTypeHandler();
        Map<String, Object> health = Map.of("height", 170, "allergies", List.of("花生"), "audiences", List.of("高血压"));
        PreparedStatement ps = mock(PreparedStatement.class);
        h.setNonNullParameter(ps, 1, health, null);
        ArgumentCaptor<String> cap = ArgumentCaptor.forClass(String.class);
        verify(ps).setString(anyInt(), cap.capture());
        assertThat(cap.getValue()).startsWith("ENC:v1:").doesNotContain("花生").doesNotContain("高血压");

        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("health_profile")).thenReturn(cap.getValue());
        assertThat(h.getNullableResult(rs, "health_profile")).isEqualTo(health);
        when(rs.getString("health_profile")).thenReturn("{\"height\":75,\"ageMonth\":12,\"audiences\":[\"宝宝辅食\"]}");
        assertThat(h.getNullableResult(rs, "health_profile")).containsEntry("ageMonth", 12);
        when(rs.getString("health_profile")).thenReturn(" ");
        assertThat(h.getNullableResult(rs, "health_profile")).isNull();
    }

    @Test
    void 未安装密钥_写入直接失败_防止明文落库() {
        FieldCrypto.uninstall();
        EncryptedStringTypeHandler h = new EncryptedStringTypeHandler();
        assertThatThrownBy(() -> h.setNonNullParameter(mock(PreparedStatement.class), 1, "13800000001", null))
                .isInstanceOf(FieldCryptoException.class);
    }
}
