package com.gudu.xsd.prode2e;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * 生产端到端测试总闸：同时满足下面三条才执行，否则整个测试类被跳过（不启动 Spring、不连库）：
 * <ol>
 *   <li>环境变量 {@code GUDU_PROD_E2E=1}（显式确认「我知道这是连生产」）；</li>
 *   <li>Maven profile {@code -Pprod-e2e}（pom 里把系统属性 {@code gudu.prod.e2e.profile} 设为 prod-e2e）；</li>
 *   <li>数据库 / Redis 主机是本机回环地址（只允许走 SSH 隧道，禁止直连公网地址）。</li>
 * </ol>
 * 普通 {@code ./mvnw test} 与 CI 缺前两条，所以永远不会误跑。
 */
public class ProdE2ECondition implements ExecutionCondition {

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        if (!"1".equals(System.getenv("GUDU_PROD_E2E"))) {
            return ConditionEvaluationResult.disabled("未设置环境变量 GUDU_PROD_E2E=1，跳过生产端到端测试");
        }
        if (!"prod-e2e".equals(System.getProperty("gudu.prod.e2e.profile"))) {
            return ConditionEvaluationResult.disabled("未指定 Maven profile -Pprod-e2e，跳过生产端到端测试");
        }
        for (String name : new String[]{"DB_HOST", "REDIS_HOST"}) {
            String host = System.getenv(name);
            if (host != null && !host.isBlank() && !isLoopback(host.trim())) {
                return ConditionEvaluationResult.disabled(
                        name + "=" + host + " 不是回环地址；生产端到端测试只允许经 SSH 隧道连接 127.0.0.1");
            }
        }
        return ConditionEvaluationResult.enabled("生产端到端测试已显式开启");
    }

    static boolean isLoopback(String host) {
        return host.equals("127.0.0.1") || host.equals("localhost") || host.equals("::1");
    }
}
