package com.gudu.xsd.modules.agent.domain.repository;

import com.gudu.xsd.modules.agent.domain.model.assess.StoredCase;
import com.gudu.xsd.modules.agent.domain.model.assess.StoredRun;

import java.util.List;
import java.util.Optional;

/** 评测用例（库内部分）与运行记录仓储。 */
public interface AgentAssessRepository {

    StoredCase saveCase(StoredCase c);

    List<StoredCase> listCases();

    StoredRun saveRun(StoredRun run);

    Optional<StoredRun> findRun(Long id);

    List<StoredRun> listRuns(int limit);

    /** 最近一次被设为基线的运行。 */
    Optional<StoredRun> baseline();

    void markBaseline(Long runId);
}
