package com.gudu.xsd.common.crypto;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 加密字符串列（手机号等）：写入时 AES-GCM 加密，读出时解密；存量明文原样读出（迁移前 / 回滚后兼容）。
 * 用法：{@code @TableField(typeHandler = EncryptedStringTypeHandler.class)}，实体 {@code autoResultMap = true}。
 * 注意：QueryWrapper 条件里的值不经 TypeHandler——按密文列查询请改用盲索引列（如 account.phone_hash）。
 */
public class EncryptedStringTypeHandler extends BaseTypeHandler<String> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType) throws SQLException {
        ps.setString(i, FieldCrypto.encrypt(parameter));
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return FieldCrypto.decrypt(rs.getString(columnName));
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return FieldCrypto.decrypt(rs.getString(columnIndex));
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return FieldCrypto.decrypt(cs.getString(columnIndex));
    }
}
