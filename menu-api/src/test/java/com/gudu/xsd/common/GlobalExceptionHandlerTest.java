package com.gudu.xsd.common;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.SQLSyntaxErrorException;

import static org.assertj.core.api.Assertions.assertThat;

/** 未知异常默认不回显根因（防 SQL/表名外泄），带 traceId；测试环境开关可回显。 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final Exception sqlError = new RuntimeException("wrap",
            new SQLSyntaxErrorException("Table 'gudu.member_secret' doesn't exist"));

    @AfterEach
    void clear() {
        MDC.clear();
    }

    @Test
    void 生产_不回显根因_带traceId() {
        MDC.put("traceId", "abc123def456");
        R<?> r = handler.all(sqlError);
        assertThat(r.getCode()).isEqualTo(1);
        assertThat(r.getMsg()).contains(GlobalExceptionHandler.GENERIC_MSG).contains("abc123def456")
                .doesNotContain("member_secret").doesNotContain("SQL");
    }

    @Test
    void 测试环境_开关打开_回显根因() {
        ReflectionTestUtils.setField(handler, "exposeDetail", true);
        assertThat(handler.all(sqlError).getMsg()).contains("member_secret");
    }

    @Test
    void 业务异常_透传业务码() {
        R<?> r = handler.biz(BizException.forbidden("无权限"));
        assertThat(r.getCode()).isEqualTo(403);
        assertThat(r.getMsg()).isEqualTo("无权限");
    }

    @Test
    void 分页参数_钳制() {
        PageQuery q = new PageQuery();
        q.setPageSize(-1);
        q.setPageNum(0);
        assertThat(q.getPageSize()).isEqualTo(15);
        assertThat(q.getPageNum()).isEqualTo(1);
        q.setPageSize(100000);
        assertThat(q.getPageSize()).isEqualTo(PageQuery.MAX_PAGE_SIZE);
    }
}
