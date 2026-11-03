package com.gudu.xsd.modules.ai;

import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

/** 判重条件会被 MyBatis-Plus 分页/租户拦截器用 JSqlParser 解析，必须是它能解析的写法（曾因 <=> 在生产报错）。 */
class AiCallLogSqlParseTest {

    @Test
    void 判重条件可被JSqlParser解析() {
        String sql = "SELECT id FROM ai_call_log WHERE (" + AiCallLogService.COUNTED_SQL + ") ORDER BY create_time DESC";
        assertThatCode(() -> CCJSqlParserUtil.parse(sql)).doesNotThrowAnyException();
    }
}
