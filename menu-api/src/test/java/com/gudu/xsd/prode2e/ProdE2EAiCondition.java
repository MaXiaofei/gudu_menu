package com.gudu.xsd.prode2e;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

/** 调用大模型的用例单独开关：除总闸外还须 {@code GUDU_PROD_E2E_AI=1}。 */
public class ProdE2EAiCondition implements ExecutionCondition {

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        return "1".equals(System.getenv("GUDU_PROD_E2E_AI"))
                ? ConditionEvaluationResult.enabled("GUDU_PROD_E2E_AI=1")
                : ConditionEvaluationResult.disabled("未设置 GUDU_PROD_E2E_AI=1，跳过会调用大模型的生产端到端用例");
    }
}
