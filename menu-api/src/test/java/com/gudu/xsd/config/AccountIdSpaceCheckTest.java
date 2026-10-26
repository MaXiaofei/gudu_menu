package com.gudu.xsd.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 账号 id / 档案 id 取值范围守护的防回退测试：V91 的常量与代码常量一致，且之后的迁移不得把 account 自增改回低值。
 */
class AccountIdSpaceCheckTest {

    static final Path SQL_DIR = Path.of("sql");

    @Test
    void 自增起点低于下限判不安全() {
        assertThat(AccountIdSpaceCheck.isSafe(AccountIdSpaceCheck.NEW_ACCOUNT_ID_FLOOR)).isTrue();
        assertThat(AccountIdSpaceCheck.isSafe(AccountIdSpaceCheck.NEW_ACCOUNT_ID_FLOOR - 1)).isFalse();
        assertThat(AccountIdSpaceCheck.isSafe(121L)).isFalse();
    }

    @Test
    void V91迁移把account自增抬到代码常量() throws IOException {
        String sql = Files.readString(SQL_DIR.resolve("V91__id_space_notification_recipient.sql"));
        Matcher m = Pattern.compile("GREATEST\\((\\d+),").matcher(sql);
        assertThat(m.find()).as("V91 应以 GREATEST(下限, 当前值, MAX(id)+1) 设置 account 自增").isTrue();
        assertThat(Long.parseLong(m.group(1))).isEqualTo(AccountIdSpaceCheck.NEW_ACCOUNT_ID_FLOOR);
        assertThat(sql).contains("ALTER TABLE account AUTO_INCREMENT");
    }

    /** V91 之后的迁移若又改 account 的 AUTO_INCREMENT，必须仍取 GREATEST(下限, …)，否则新账号 id 会回到与档案 id 重叠的区间。 */
    @Test
    void V91之后的迁移不得降低account自增() throws IOException {
        List<Path> later;
        try (Stream<Path> files = Files.list(SQL_DIR)) {
            later = files.filter(p -> p.getFileName().toString().matches("V\\d+__.*\\.sql"))
                    .filter(p -> version(p) > 91).toList();
        }
        for (Path p : later) {
            String sql = Files.readString(p);
            if (Pattern.compile("(?i)ALTER\\s+TABLE\\s+account\\s+AUTO_INCREMENT").matcher(sql).find()) {
                assertThat(sql).as(p.getFileName() + " 修改了 account 自增，必须保持 >= 下限")
                        .contains("GREATEST(" + AccountIdSpaceCheck.NEW_ACCOUNT_ID_FLOOR);
            }
        }
    }

    private static int version(Path p) {
        return Integer.parseInt(p.getFileName().toString().replaceAll("^V(\\d+)__.*$", "$1"));
    }
}
