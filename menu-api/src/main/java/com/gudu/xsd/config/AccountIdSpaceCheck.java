package com.gudu.xsd.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.sql.ResultSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 账号 id / 成员档案 id 取值范围守护（V91）。
 *
 * <p>账号（account.id = 登录 loginId）与成员档案（member.id）是两张表各自的自增，存量账号又沿用旧 member.id，
 * 新账号与新档案会撞号（账号 120 与档案 120 是不同的人），按档案归属的数据一旦误用账号 id 比较就会串号。
 * V91 把 account 的 AUTO_INCREMENT 抬到 {@value #NEW_ACCOUNT_ID_FLOOR} 以上，使新账号 id 与档案 id 取值范围永不重叠。
 *
 * <p>本检查在启动后读取 account 的自增起点，低于下限时打 ERROR 日志（不阻断启动：库迁移与应用发布有先后，
 * 阻断会把一次发布顺序问题放大成事故）。迁移脚本里的常量由单测与本常量对齐，防止以后被改回。
 * 用原生 JDBC 读 {@code SHOW CREATE TABLE account} 解析 {@code AUTO_INCREMENT=}：MySQL 8 的 information_schema.TABLES
 * 默认缓存统计信息（information_schema_stats_expiry，默认 24h），会读到旧自增值而误报；SHOW CREATE TABLE 取实时值。
 */
@Slf4j
@Component
public class AccountIdSpaceCheck implements ApplicationRunner {

    /** 新账号 id 下限（V91 写入 account 的 AUTO_INCREMENT；档案 id 远小于此）。 */
    public static final long NEW_ACCOUNT_ID_FLOOR = 10_000_000L;

    private static final Pattern AUTO_INCREMENT = Pattern.compile("\\bAUTO_INCREMENT\\s*=\\s*(\\d+)", Pattern.CASE_INSENSITIVE);

    private final DataSource dataSource;

    public AccountIdSpaceCheck(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** 自增起点是否满足 V91 约定。 */
    public static boolean isSafe(long accountAutoIncrement) {
        return accountAutoIncrement >= NEW_ACCOUNT_ID_FLOOR;
    }

    /**
     * 从建表语句解析表级 AUTO_INCREMENT 起点。MySQL 在计数器为 1 时省略该子句，此时返回 1（最不安全值）。
     * 列定义里的 {@code AUTO_INCREMENT}（无 {@code =}）不会被匹配。
     */
    public static long parseAutoIncrement(String createTableSql) {
        if (createTableSql == null) {
            return 1L;
        }
        Matcher m = AUTO_INCREMENT.matcher(createTableSql);
        return m.find() ? Long.parseLong(m.group(1)) : 1L;
    }

    @Override
    public void run(ApplicationArguments args) {
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SHOW CREATE TABLE account")) {
            if (rs.next()) {
                long next = parseAutoIncrement(rs.getString(2));
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
