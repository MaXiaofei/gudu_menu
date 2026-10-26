package com.gudu.xsd.common.crypto;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 字段加密存量迁移（mini-release-compliance B.3）。MySQL 没有 AES-GCM，只能在应用里逐行处理。
 *
 * <ul>
 *   <li>{@link #encryptAll()}：明文 → 密文、旧版本密文 → active 版本密文（密钥轮换），并补齐/校正 account.phone_hash；</li>
 *   <li>{@link #decryptAll()}：全部还原为明文、清空 phone_hash（回滚前执行，之后才能跑 sql/rollback/V88-V89 脚本）。</li>
 * </ul>
 * 可重复执行：只处理需要变化的行；按主键分批（每批独立自动提交，无长事务）；
 * 更新带旧值条件（{@code WHERE id=? AND col=旧值}），与在线写入并发时不会覆盖新数据（被跳过的行下次再跑即可）。
 *
 * <p>覆盖：account.phone（+phone_hash）、member.health_profile、member.phone（旧列，仅 11 位手机号；
 * admin / wx_ / cm_ / demo 等占位登录名不是个人信息，保持明文，E2E 种子与 V77 迁移依赖这些占位值）。
 * 直接用 JdbcTemplate：平台级运维操作，全表处理，不经租户插件（已在 TenantGuardTest 白名单登记）。
 */
@Slf4j
public class FieldEncryptionMigrator {

    private static final Pattern MOBILE = Pattern.compile("^1\\d{10}$");

    private final JdbcTemplate jdbc;
    private final FieldCipher cipher;
    private final int batch;

    public static FieldEncryptionMigrator of(javax.sql.DataSource dataSource, FieldCipher cipher, int batch) {
        return new FieldEncryptionMigrator(new JdbcTemplate(dataSource), cipher, batch);
    }

    public FieldEncryptionMigrator(JdbcTemplate jdbc, FieldCipher cipher, int batch) {
        this.jdbc = jdbc;
        this.cipher = cipher;
        this.batch = Math.max(1, batch);
    }

    /** 迁移结果：各列改动行数、并发跳过与冲突数。 */
    public record Report(int accountPhone, int memberPhone, int memberHealth, int skipped, int conflicts) {
    }

    public Report encryptAll() {
        Counter c = new Counter();
        forEachBatch("SELECT id, phone, phone_hash FROM account WHERE id > ? ORDER BY id LIMIT ?", row -> {
            long id = ((Number) row.get("id")).longValue();
            String stored = (String) row.get("phone");
            String hash = (String) row.get("phone_hash");
            if (stored == null) {
                if (hash != null) c.accountPhone += jdbc.update("UPDATE account SET phone_hash = NULL WHERE id = ? AND phone IS NULL", id);
                return;
            }
            String plain = cipher.decrypt(stored);
            String target = cipher.needsReencrypt(stored) ? cipher.encrypt(plain) : stored;
            String targetHash = cipher.blindIndex(plain);
            if (target.equals(stored) && Objects.equals(targetHash, hash)) return;
            try {
                int n = jdbc.update("UPDATE account SET phone = ?, phone_hash = ? WHERE id = ? AND phone = ?",
                        target, targetHash, id, stored);
                if (n == 0) c.skipped++;
                c.accountPhone += n;
            } catch (DuplicateKeyException e) {
                c.conflicts++;
                log.error("account#{} 手机号盲索引与其他账号重复，未迁移（需人工核查重复账号）", id);
            }
        });
        forEachBatch("SELECT id, phone, health_profile FROM member WHERE id > ? ORDER BY id LIMIT ?", row -> {
            long id = ((Number) row.get("id")).longValue();
            String phone = (String) row.get("phone");
            if (phone != null && (FieldCipher.isEncrypted(phone) ? cipher.needsReencrypt(phone) : MOBILE.matcher(phone).matches())) {
                int n = jdbc.update("UPDATE member SET phone = ? WHERE id = ? AND phone = ?",
                        cipher.encrypt(cipher.decrypt(phone)), id, phone);
                if (n == 0) c.skipped++;
                c.memberPhone += n;
            }
            String health = (String) row.get("health_profile");
            if (health != null && cipher.needsReencrypt(health)) {
                int n = jdbc.update("UPDATE member SET health_profile = ? WHERE id = ? AND health_profile = ?",
                        cipher.encrypt(cipher.decrypt(health)), id, health);
                if (n == 0) c.skipped++;
                c.memberHealth += n;
            }
        });
        return c.report();
    }

    public Report decryptAll() {
        Counter c = new Counter();
        forEachBatch("SELECT id, phone, phone_hash FROM account WHERE id > ? ORDER BY id LIMIT ?", row -> {
            long id = ((Number) row.get("id")).longValue();
            String stored = (String) row.get("phone");
            if (!FieldCipher.isEncrypted(stored) && row.get("phone_hash") == null) return;
            int n = stored == null
                    ? jdbc.update("UPDATE account SET phone_hash = NULL WHERE id = ? AND phone IS NULL", id)
                    : jdbc.update("UPDATE account SET phone = ?, phone_hash = NULL WHERE id = ? AND phone = ?",
                    cipher.decrypt(stored), id, stored);
            if (n == 0) c.skipped++;
            c.accountPhone += n;
        });
        forEachBatch("SELECT id, phone, health_profile FROM member WHERE id > ? ORDER BY id LIMIT ?", row -> {
            long id = ((Number) row.get("id")).longValue();
            String phone = (String) row.get("phone");
            if (FieldCipher.isEncrypted(phone)) {
                int n = jdbc.update("UPDATE member SET phone = ? WHERE id = ? AND phone = ?", cipher.decrypt(phone), id, phone);
                if (n == 0) c.skipped++;
                c.memberPhone += n;
            }
            String health = (String) row.get("health_profile");
            if (FieldCipher.isEncrypted(health)) {
                int n = jdbc.update("UPDATE member SET health_profile = ? WHERE id = ? AND health_profile = ?",
                        cipher.decrypt(health), id, health);
                if (n == 0) c.skipped++;
                c.memberHealth += n;
            }
        });
        return c.report();
    }

    private void forEachBatch(String sql, java.util.function.Consumer<Map<String, Object>> handler) {
        long lastId = 0;
        while (true) {
            List<Map<String, Object>> rows = jdbc.queryForList(sql, lastId, batch);
            for (Map<String, Object> row : rows) {
                handler.accept(row);
                lastId = ((Number) row.get("id")).longValue();
            }
            if (rows.size() < batch) return;
        }
    }

    private static final class Counter {
        int accountPhone;
        int memberPhone;
        int memberHealth;
        int skipped;
        int conflicts;

        Report report() {
            return new Report(accountPhone, memberPhone, memberHealth, skipped, conflicts);
        }
    }
}
