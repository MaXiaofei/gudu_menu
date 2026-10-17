package com.gudu.xsd.modules.agent.domain.model.assess;

import java.math.BigDecimal;
import java.util.List;

/**
 * 一条用例的一次运行结果。
 *
 * @param confirmCompliant 写操作全部以待确认动作出现、确认前夹具零变化
 * @param judged           是否经 LLM 裁判判过 rubric（mock 模式下不判）
 * @param mustHit          rubric.must 命中数
 * @param mustNotHit       命中任一 mustNot（该次直接判失败）
 */
public record RunOutcome(boolean toolsSelected, int argsMatched, int argsTotal, boolean confirmCompliant,
                         boolean judged, int mustHit, int mustTotal, boolean mustNotHit, boolean otherChecksOk,
                         BigDecimal costFen, Long firstTokenMs, long ms, List<String> actualTools,
                         List<String> failReasons) {

    public RunOutcome {
        actualTools = actualTools == null ? List.of() : List.copyOf(actualTools);
        failReasons = failReasons == null ? List.of() : List.copyOf(failReasons);
        costFen = costFen == null ? BigDecimal.ZERO : costFen;
    }

    /** 本次运行是否通过：工具、参数、确认、其他检查、mustNot 全部满足（must 覆盖率单独计指标）。 */
    public boolean pass() {
        return toolsSelected && argsMatched == argsTotal && confirmCompliant && otherChecksOk && !mustNotHit;
    }
}
