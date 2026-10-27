package com.gudu.xsd.config;

import com.baomidou.mybatisplus.autoconfigure.ConfigurationCustomizer;
import com.gudu.xsd.common.crypto.FieldCipher;
import com.gudu.xsd.common.crypto.FieldCrypto;
import com.gudu.xsd.common.crypto.FieldEncryptionMigrator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * 字段级加密配置（mini-release-compliance B.3）。
 *
 * <pre>
 * gudu.field-crypto.keys           GUDU_FIELD_CRYPTO_KEYS     "1:&lt;base64 32 字节&gt;[,2:&lt;base64&gt;]"（轮换时追加新版本，旧版本保留）
 * gudu.field-crypto.active-version GUDU_FIELD_CRYPTO_ACTIVE   新数据用哪个版本加密（默认 1）
 * gudu.field-crypto.hmac-key       GUDU_FIELD_CRYPTO_HMAC_KEY 手机号盲索引密钥（≥ 32 字节字符串，不随加密密钥轮换）
 * gudu.field-crypto.migrate        GUDU_FIELD_CRYPTO_MIGRATE  none（默认）/ encrypt / decrypt：一次性存量迁移，执行完退出进程
 * </pre>
 * 密钥缺失启动即失败（不允许明文落库）；dev/test 在 application-dev/test.yml 用固定测试密钥。
 *
 * <p>安装时机：{@link ConfigurationCustomizer} 由 MyBatis-Plus 自动配置在创建 SqlSessionFactory 时注入，
 * 依赖本 bean 即保证任何 Mapper 执行前密钥已就位。
 */
@Slf4j
@Configuration
public class FieldCryptoConfig {

    @Bean
    public FieldCipher fieldCipher(@Value("${gudu.field-crypto.keys:}") String keys,
                                   @Value("${gudu.field-crypto.active-version:1}") int activeVersion,
                                   @Value("${gudu.field-crypto.hmac-key:}") String hmacKey) {
        FieldCipher cipher = FieldCipher.fromConfig(keys, activeVersion, hmacKey);
        FieldCrypto.install(cipher);
        log.info("字段加密已启用：active 密钥版本 v{}", cipher.activeVersion());
        return cipher;
    }

    @Bean
    public ConfigurationCustomizer fieldCryptoReady(FieldCipher fieldCipher) {
        return configuration -> {
            // 仅用于保证安装顺序（见类注释）
        };
    }

    /**
     * 存量迁移入口（一次性，跑完退出）：
     * {@code java -jar menu-api.jar --spring.profiles.active=prod --gudu.field-crypto.migrate=encrypt}（回滚前用 decrypt）。
     * 不要加 {@code --spring.main.web-application-type=none}：非 Web 上下文里 Controller 注入不到 HttpServletRequest，启动失败。
     */
    @Bean
    public ApplicationRunner fieldEncryptionMigrationRunner(@Value("${gudu.field-crypto.migrate:none}") String mode,
                                                            @Value("${gudu.field-crypto.migrate-batch:500}") int batch,
                                                            FieldCipher cipher, DataSource dataSource,
                                                            ApplicationContext ctx) {
        return args -> {
            String m = mode == null ? "none" : mode.trim().toLowerCase();
            if ("none".equals(m) || m.isEmpty()) return;
            FieldEncryptionMigrator migrator = FieldEncryptionMigrator.of(dataSource, cipher, batch);
            FieldEncryptionMigrator.Report report = switch (m) {
                case "encrypt" -> migrator.encryptAll();
                case "decrypt" -> migrator.decryptAll();
                default -> throw new IllegalArgumentException("gudu.field-crypto.migrate 只能是 none/encrypt/decrypt：" + mode);
            };
            log.warn("字段加密存量迁移 [{}] 完成：{}", m, report);
            System.exit(SpringApplication.exit(ctx, () -> 0));
        };
    }
}
