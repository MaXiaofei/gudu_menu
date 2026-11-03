package com.gudu.xsd.prode2e;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 兜底清理入口：生产端到端测试中途失败 / 进程被杀后，手动跑一次，把所有 {@code E2E_PROD_} 数据按前缀精确清掉。
 *
 * <p>跑法（需同时满足总闸 GUDU_PROD_E2E=1、-Pprod-e2e，以及本类自己的 GUDU_PROD_E2E_CLEANUP=1）：
 * <pre>
 * GUDU_PROD_E2E=1 GUDU_PROD_E2E_CLEANUP=1 ./mvnw test -Pprod-e2e -Dtest=GuduProdE2ECleanupTest
 * </pre>
 * 清理规则见 {@link ProdE2ECleaner}：只删昵称 / openid / 名称 / 标题以 E2E_PROD_ 开头的数据，
 * 以及它们名下的家庭、档案、业务数据；台账（target/prod-e2e-ledger.txt）里记过的账号；只删自己写的 Redis 键。
 */
@ExtendWith(GuduProdE2ECleanupTest.CleanupGate.class)
class GuduProdE2ECleanupTest extends ProdE2EBase {

    static class CleanupGate implements ExecutionCondition {
        @Override
        public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
            return "1".equals(System.getenv("GUDU_PROD_E2E_CLEANUP"))
                    ? ConditionEvaluationResult.enabled("GUDU_PROD_E2E_CLEANUP=1")
                    : ConditionEvaluationResult.disabled("未设置 GUDU_PROD_E2E_CLEANUP=1，跳过兜底清理入口");
        }
    }

    @Override
    protected boolean withFixtures() {
        return false;
    }

    @Test
    void 兜底清理_按E2E_PROD_前缀精确清理_并自检无残留() {
        ProdE2ECleaner.Result r = newCleaner().cleanAll();
        System.out.println("[prod-e2e] 兜底清理 " + r);
        assertThat(newCleaner().residue()).as("清理后残留").isEmpty();
        assertThat(r.notes()).as("存在未处理项，请人工核对").isEmpty();
    }
}
