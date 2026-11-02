package com.gudu.xsd.modules.backoffice.infrastructure;

import com.gudu.xsd.modules.backoffice.domain.model.AccountSearch;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 后台账号目录：被合并软删除的账号（V98）默认不出现在用户列表，显式筛「已合并」时才可见（审计用）。 */
class JdbcAccountDirectoryQueryTest {

    @Test
    void 默认与按其他状态筛选_排除软删除账号() {
        List<Object> args = new ArrayList<>();
        assertThat(JdbcAccountDirectoryQuery.where(new AccountSearch(null, null, null, null, false), args))
                .contains("a.deleted_at IS NULL");
        assertThat(JdbcAccountDirectoryQuery.where(new AccountSearch("张", null, null, "ACTIVE", false), args))
                .contains("a.deleted_at IS NULL");
    }

    @Test
    void 显式筛已合并_才能看到软删除账号() {
        assertThat(JdbcAccountDirectoryQuery.where(new AccountSearch(null, null, null, "MERGED", false), new ArrayList<>()))
                .doesNotContain("a.deleted_at IS NULL").contains("a.status = ?");
    }
}
