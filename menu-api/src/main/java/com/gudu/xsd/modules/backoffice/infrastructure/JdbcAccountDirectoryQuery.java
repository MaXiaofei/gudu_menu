package com.gudu.xsd.modules.backoffice.infrastructure;

import com.gudu.xsd.common.crypto.FieldCrypto;
import com.gudu.xsd.modules.backoffice.domain.model.AccountRow;
import com.gudu.xsd.modules.backoffice.domain.model.AccountSearch;
import com.gudu.xsd.modules.backoffice.domain.repository.AccountDirectoryQuery;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 账号目录 JDBC 实现（只读、平台级）：只访问全局表 account / account_ban / family_membership / family，
 * 不碰任何家庭域表（不展示家庭私有数据）。手写 SQL 已在 TenantGuardTest 白名单登记。
 * 手机号只出脱敏值、openid 只出末 6 位。
 */
@Repository
public class JdbcAccountDirectoryQuery implements AccountDirectoryQuery {

    static final String SELECT = "SELECT a.id, a.nickname, a.phone, a.wx_openid, a.status, a.platform_role, a.created_at,"
            + " a.deactivated_at, b.reason AS ban_reason, b.banned_at,"
            + " (SELECT COUNT(*) FROM family_membership fm JOIN family f ON f.id = fm.family_id AND f.status = 'ACTIVE'"
            + "  WHERE fm.account_id = a.id) AS family_count"
            + " FROM account a LEFT JOIN account_ban b ON b.account_id = a.id";
    static final String COUNT = "SELECT COUNT(*) FROM account a LEFT JOIN account_ban b ON b.account_id = a.id";

    private final JdbcTemplate jdbc;

    /** 用 @Primary 业务库数据源自建 JdbcTemplate（容器里唯一的 JdbcTemplate bean 指向 PG 向量库，见 JdbcOpsStatsQuery）。 */
    public JdbcAccountDirectoryQuery(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @Override
    public List<AccountRow> search(AccountSearch s, int offset, int limit) {
        List<Object> args = new ArrayList<>();
        String where = where(s, args);
        args.add(Math.max(0, offset));
        args.add(Math.max(1, limit));
        return jdbc.query(SELECT + where + " ORDER BY a.id DESC LIMIT ?, ?", (rs, i) -> row(rs), args.toArray());
    }

    @Override
    public long count(AccountSearch s) {
        List<Object> args = new ArrayList<>();
        Long n = jdbc.queryForObject(COUNT + where(s, args), Long.class, args.toArray());
        return n == null ? 0 : n;
    }

    static String where(AccountSearch s, List<Object> args) {
        StringBuilder w = new StringBuilder(" WHERE 1 = 1");
        if (s.keyword() != null) {
            if (s.keyword().matches("\\d{1,18}")) {
                w.append(" AND (a.id = ? OR a.nickname LIKE ? ESCAPE '\\\\')");
                args.add(Long.valueOf(s.keyword()));
            } else {
                w.append(" AND a.nickname LIKE ? ESCAPE '\\\\'");
            }
            args.add("%" + escapeLike(s.keyword()) + "%");
        }
        if (s.openidTail() != null) {
            w.append(" AND a.wx_openid LIKE ? ESCAPE '\\\\'");
            args.add("%" + escapeLike(s.openidTail()));
        }
        if (s.familyId() != null) {
            w.append(" AND EXISTS (SELECT 1 FROM family_membership fm2 WHERE fm2.account_id = a.id AND fm2.family_id = ?)");
            args.add(s.familyId());
        }
        if (s.status() != null) {
            w.append(" AND a.status = ?");
            args.add(s.status());
        }
        if (s.bannedOnly()) w.append(" AND b.account_id IS NOT NULL");
        return w.toString();
    }

    static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    static String maskPhone(String stored) {
        if (stored == null || stored.isBlank()) return null;
        String phone;
        try {
            phone = FieldCrypto.decrypt(stored); // B.3 起 account.phone 为密文
        } catch (RuntimeException e) {
            return "已加密";
        }
        if (phone == null || phone.isBlank()) return null;
        if (phone.length() < 7) return phone.charAt(0) + "***";
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    static String openidTail(String openid) {
        if (openid == null || openid.isBlank()) return null;
        return openid.length() <= 6 ? openid : openid.substring(openid.length() - 6);
    }

    private static AccountRow row(ResultSet rs) throws SQLException {
        return new AccountRow(rs.getLong("id"), rs.getString("nickname"), maskPhone(rs.getString("phone")),
                openidTail(rs.getString("wx_openid")), rs.getString("status"), rs.getString("platform_role"),
                rs.getInt("family_count"), rs.getString("ban_reason"), time(rs, "banned_at"),
                time(rs, "created_at"), time(rs, "deactivated_at"));
    }

    private static LocalDateTime time(ResultSet rs, String col) throws SQLException {
        Timestamp t = rs.getTimestamp(col);
        return t == null ? null : t.toLocalDateTime();
    }
}
