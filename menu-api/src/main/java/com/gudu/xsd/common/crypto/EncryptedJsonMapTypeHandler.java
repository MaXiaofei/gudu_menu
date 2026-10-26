package com.gudu.xsd.common.crypto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

/**
 * 加密 JSON 对象列（成员健康档案 member.health_profile）：Map → JSON → AES-GCM 密文；读出反向。
 * 存量明文 JSON（V88 前写入 / 回滚后）原样解析。列类型须为 TEXT（V88 由 JSON 改 TEXT，密文不是合法 JSON）。
 */
public class EncryptedJsonMapTypeHandler extends BaseTypeHandler<Map<String, Object>> {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Map<String, Object> parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setString(i, FieldCrypto.encrypt(toJson(parameter)));
    }

    @Override
    public Map<String, Object> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parse(rs.getString(columnName));
    }

    @Override
    public Map<String, Object> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parse(rs.getString(columnIndex));
    }

    @Override
    public Map<String, Object> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parse(cs.getString(columnIndex));
    }

    static String toJson(Map<String, Object> value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new FieldCryptoException("健康档案序列化失败", e);
        }
    }

    static Map<String, Object> parse(String stored) {
        if (stored == null || stored.isBlank()) return null;
        String json = FieldCrypto.decrypt(stored);
        try {
            return JSON.readValue(json, MAP);
        } catch (JsonProcessingException e) {
            throw new FieldCryptoException("健康档案解析失败", e);
        }
    }
}
