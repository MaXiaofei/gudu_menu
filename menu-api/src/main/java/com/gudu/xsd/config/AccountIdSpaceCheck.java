package com.gudu.xsd.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * 账号 id / 成员档案 id 取值范围守护（V91）。
 *
 * <p>账号（account.id = 登录 loginId）与成员档案（member.id）是两张表各自的自增，存量账号又沿用旧 member.id，
 * 新账号与新档案会撞号（账号 120 与档案 120 是不同的人），按档案归属的数据一旦误用账号 id 比较就会串号。
 * V91 把 account 的 AUTO_INCREMENT 抬到 {@value #NEW_ACCOUNT_ID_FLOOR} 以上，使新账号 id 与档案 id 取值范围永不重叠。
 *
 * <p>本检查在启动后读取 account 的自增起点，低于下限时打 ERROR 日志（不阻断启动：库迁移与应用发布有先后，
 * 阻断会把一次发布顺序问题放大成事故）。迁移脚本里的常量由单测与本常量对齐，防止以后被改回。
 * 用原生 JDBC 读元数据（只读 information_schema，不碰家庭域表）。
 */
@Slf4j
@Component
public class AccountIdSpaceCheck implements ApplicationRunner {

    /** 新账号 id 下限（V91 写入 account 的 AUTO_INCREMENT；档案 id 远小于此）。 */
    public static final long NEW_ACCOUNT_ID_FLOOR = 10_000_000L;

    private final DataSource dataSource;

    public AccountIdSpaceCheck(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** 自增起点是否满足 V91 约定。 */
    public static boolean isSafe(long accountAutoIncrement) {
        return accountAutoIncrement >= NEW_ACCOUNT_ID_FLOOR;
    }

    @Override
    public void run(ApplicationArguments args) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT AUTO_INCREMENT FROM information_schema.TABLES "
                     + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'account'");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                long next = rs.getLong(1);
                if (!isSafe(next)) {
                    log.error("account 自增起点 {} < {}：新账号 id 可能与成员档案 id 撞号、导致按档案归属的数据串号。"
                            + "请执行迁移 V91__id_space_notification_recipient.sql", next, NEW_ACCOUNT_ID_FLOOR);
                }
            }
        } catch (Exception e) {
            log.warn("账号 id 取值范围自检跳过：{}", e.getMessage());
        }
    }
}
