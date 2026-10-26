package com.gudu.xsd.common.crypto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 存量加密迁移（B.3）：H2（MySQL 模式）内存库模拟 account / member 关键列，验证加密、可重复执行、密钥轮换、回滚解密。
 */
class FieldEncryptionMigratorTest {

    JdbcTemplate jdbc;
    FieldCipher v1 = FieldCipherTest.v1();

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("CREATE TABLE account (id BIGINT PRIMARY KEY, phone VARCHAR(255), phone_hash CHAR(64), "
                + "CONSTRAINT uk_account_phone_hash UNIQUE (phone_hash))");
        jdbc.execute("CREATE TABLE member (id BIGINT PRIMARY KEY, phone VARCHAR(255), health_profile TEXT)");
        jdbc.update("INSERT INTO account(id, phone) VALUES (1, '13800000001'), (2, 'admin'), (3, NULL)");
        jdbc.update("INSERT INTO account(id, phone, phone_hash) VALUES (4, NULL, 'stale')");
        jdbc.update("INSERT INTO member(id, phone, health_profile) VALUES "
                + "(1, '13800000001', '{\"height\":175}'), (2, 'admin', NULL), (3, 'wx_oABC', '{}'), "
                + "(4, 'demo-mom', '{\"audiences\":[\"宝宝辅食\"]}'), (5, NULL, NULL)");
    }

    private Map<String, Object> row(String table, long id) {
        return jdbc.queryForMap("SELECT * FROM " + table + " WHERE id = ?", id);
    }

    @Test
    void 加密_手机号与健康档案变密文_补盲索引_占位登录名保持明文() {
        FieldEncryptionMigrator.Report r = new FieldEncryptionMigrator(jdbc, v1, 2).encryptAll();
        assertThat(r.conflicts()).isZero();

        String p1 = (String) row("account", 1).get("phone");
        assertThat(p1).startsWith("ENC:v1:");
        assertThat(v1.decrypt(p1)).isEqualTo("13800000001");
        assertThat(row("account", 1).get("phone_hash")).isEqualTo(v1.blindIndex("13800000001"));
        assertThat(v1.decrypt((String) row("account", 2).get("phone"))).isEqualTo("admin");
        assertThat(row("account", 2).get("phone_hash")).isEqualTo(v1.blindIndex("admin"));
        assertThat(row("account", 3).get("phone")).isNull();
        assertThat(row("account", 4).get("phone_hash")).as("无手机号的残留摘要清空").isNull();

        assertThat((String) row("member", 1).get("phone")).startsWith("ENC:v1:");
        assertThat(row("member", 2).get("phone")).isEqualTo("admin");
        assertThat(row("member", 3).get("phone")).isEqualTo("wx_oABC");
        assertThat(row("member", 4).get("phone")).isEqualTo("demo-mom");
        assertThat(v1.decrypt((String) row("member", 1).get("health_profile"))).isEqualTo("{\"height\":175}");
        assertThat((String) row("member", 3).get("health_profile")).startsWith("ENC:v1:");
        assertThat(row("member", 5).get("health_profile")).isNull();
        assertThat(r.accountPhone()).isEqualTo(3);
        assertThat(r.memberPhone()).isEqualTo(1);
        assertThat(r.memberHealth()).isEqualTo(3);
    }

    @Test
    void 可重复执行_第二次无改动() {
        FieldEncryptionMigrator m = new FieldEncryptionMigrator(jdbc, v1, 2);
        m.encryptAll();
        String before = (String) row("account", 1).get("phone");
        FieldEncryptionMigrator.Report again = m.encryptAll();
        assertThat(again.accountPhone() + again.memberPhone() + again.memberHealth()).isZero();
        assertThat(row("account", 1).get("phone")).isEqualTo(before);
    }

    @Test
    void 密钥轮换_旧版本密文重加密为新版本_盲索引不变() {
        new FieldEncryptionMigrator(jdbc, v1, 500).encryptAll();
        FieldCipher v2 = FieldCipher.fromConfig("1:" + FieldCipherTest.K1 + ",2:" + FieldCipherTest.K2, 2,
                FieldCipherTest.HMAC);
        FieldEncryptionMigrator.Report r = new FieldEncryptionMigrator(jdbc, v2, 500).encryptAll();
        assertThat(r.accountPhone()).isEqualTo(2);
        String p1 = (String) row("account", 1).get("phone");
        assertThat(p1).startsWith("ENC:v2:");
        assertThat(v2.decrypt(p1)).isEqualTo("13800000001");
        assertThat(row("account", 1).get("phone_hash")).isEqualTo(v1.blindIndex("13800000001"));
        assertThat((String) row("member", 1).get("health_profile")).startsWith("ENC:v2:");
    }

    @Test
    void 回滚解密_全部还原明文_清空盲索引_再加密仍正确() {
        FieldEncryptionMigrator m = new FieldEncryptionMigrator(jdbc, v1, 3);
        m.encryptAll();
        m.decryptAll();
        assertThat(row("account", 1).get("phone")).isEqualTo("13800000001");
        assertThat(row("account", 1).get("phone_hash")).isNull();
        assertThat(row("account", 2).get("phone")).isEqualTo("admin");
        assertThat(row("member", 1).get("phone")).isEqualTo("13800000001");
        assertThat(row("member", 1).get("health_profile")).isEqualTo("{\"height\":175}");
        assertThat(row("member", 4).get("health_profile")).isEqualTo("{\"audiences\":[\"宝宝辅食\"]}");
        // 解密也可重复执行
        FieldEncryptionMigrator.Report again = m.decryptAll();
        assertThat(again.accountPhone() + again.memberPhone() + again.memberHealth()).isZero();
        m.encryptAll();
        assertThat(v1.decrypt((String) row("account", 1).get("phone"))).isEqualTo("13800000001");
    }

    @Test
    void 盲索引冲突_记为冲突不中断() {
        jdbc.update("INSERT INTO account(id, phone, phone_hash) VALUES (9, ?, ?)",
                v1.encrypt("13800000001"), v1.blindIndex("13800000001"));
        FieldEncryptionMigrator.Report r = new FieldEncryptionMigrator(jdbc, v1, 500).encryptAll();
        assertThat(r.conflicts()).isEqualTo(1);
        assertThat(row("account", 1).get("phone")).as("冲突行保持原样待人工处理").isEqualTo("13800000001");
        assertThat((String) row("account", 2).get("phone")).startsWith("ENC:v1:");
    }
}
