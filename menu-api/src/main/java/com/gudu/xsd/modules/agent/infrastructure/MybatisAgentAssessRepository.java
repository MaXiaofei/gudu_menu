package com.gudu.xsd.modules.agent.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.gudu.xsd.modules.agent.domain.model.assess.StoredCase;
import com.gudu.xsd.modules.agent.domain.model.assess.StoredRun;
import com.gudu.xsd.modules.agent.domain.repository.AgentAssessRepository;
import com.gudu.xsd.modules.agent.infrastructure.mapper.AgentAssessCaseMapper;
import com.gudu.xsd.modules.agent.infrastructure.mapper.AgentAssessRunMapper;
import com.gudu.xsd.modules.agent.infrastructure.po.AgentAssessCasePO;
import com.gudu.xsd.modules.agent.infrastructure.po.AgentAssessRunPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class MybatisAgentAssessRepository implements AgentAssessRepository {

    private final AgentAssessCaseMapper caseMapper;
    private final AgentAssessRunMapper runMapper;

    @Override
    public StoredCase saveCase(StoredCase c) {
        AgentAssessCasePO po = caseMapper.selectOne(new QueryWrapper<AgentAssessCasePO>().eq("case_id", c.caseId()));
        boolean insert = po == null;
        if (insert) po = new AgentAssessCasePO();
        po.setCaseId(c.caseId());
        po.setSplit(c.split());
        po.setCategory(c.category());
        po.setJson(c.json());
        po.setSource(c.source());
        po.setFromTraceId(c.fromTraceId());
        po.setStatus(c.status());
        if (insert) caseMapper.insert(po);
        else caseMapper.updateById(po);
        return new StoredCase(po.getId(), c.caseId(), c.split(), c.category(), c.json(), c.source(), c.fromTraceId(),
                c.status(), po.getCreatedAt());
    }

    @Override
    public List<StoredCase> listCases() {
        return caseMapper.selectList(new QueryWrapper<AgentAssessCasePO>().orderByAsc("id")).stream()
                .map(p -> new StoredCase(p.getId(), p.getCaseId(), p.getSplit(), p.getCategory(), p.getJson(),
                        p.getSource(), p.getFromTraceId(), p.getStatus(), p.getCreatedAt()))
                .toList();
    }

    @Override
    public StoredRun saveRun(StoredRun r) {
        AgentAssessRunPO po = new AgentAssessRunPO();
        po.setId(r.id());
        po.setPromptVersion(r.promptVersion());
        po.setProvider(r.provider());
        po.setStatus(r.status());
        po.setToolAccuracy(bd(r.toolAccuracy()));
        po.setArgAccuracy(bd(r.argAccuracy()));
        po.setPointCoverage(bd(r.pointCoverage()));
        po.setAvgCostFen(r.avgCostFen());
        po.setP95Ms(r.p95Ms() == null ? null : r.p95Ms().intValue());
        po.setPassed(r.passed() == null ? null : (r.passed() ? 1 : 0));
        po.setHardGateFailures(r.hardGateFailures());
        po.setBaseline(r.baseline() ? 1 : 0);
        po.setReportJson(r.reportJson());
        po.setError(r.error() == null ? null : r.error().length() > 500 ? r.error().substring(0, 500) : r.error());
        po.setFinishedAt(r.finishedAt());
        if (po.getId() == null) runMapper.insert(po);
        else runMapper.updateById(po);
        return findRun(po.getId()).orElseThrow();
    }

    @Override
    public Optional<StoredRun> findRun(Long id) {
        return Optional.ofNullable(id == null ? null : runMapper.selectById(id)).map(MybatisAgentAssessRepository::toDomain);
    }

    @Override
    public List<StoredRun> listRuns(int limit) {
        return runMapper.selectList(new QueryWrapper<AgentAssessRunPO>()
                        .select("id", "prompt_version", "provider", "status", "tool_accuracy", "arg_accuracy",
                                "point_coverage", "avg_cost_fen", "p95_ms", "passed", "hard_gate_failures", "baseline",
                                "error", "created_at", "finished_at")
                        .orderByDesc("id").last("LIMIT " + limit))
                .stream().map(MybatisAgentAssessRepository::toDomain).toList();
    }

    @Override
    public Optional<StoredRun> baseline() {
        return Optional.ofNullable(runMapper.selectOne(new QueryWrapper<AgentAssessRunPO>().eq("baseline", 1)
                .orderByDesc("id").last("LIMIT 1"))).map(MybatisAgentAssessRepository::toDomain);
    }

    @Override
    public void markBaseline(Long runId) {
        runMapper.update(null, new UpdateWrapper<AgentAssessRunPO>().set("baseline", 0).eq("baseline", 1));
        runMapper.update(null, new UpdateWrapper<AgentAssessRunPO>().set("baseline", 1).eq("id", runId));
    }

    private static StoredRun toDomain(AgentAssessRunPO p) {
        return new StoredRun(p.getId(), p.getPromptVersion(), p.getProvider(), p.getStatus(), dbl(p.getToolAccuracy()),
                dbl(p.getArgAccuracy()), dbl(p.getPointCoverage()), p.getAvgCostFen(),
                p.getP95Ms() == null ? null : p.getP95Ms().longValue(),
                p.getPassed() == null ? null : p.getPassed() == 1, p.getHardGateFailures(),
                Integer.valueOf(1).equals(p.getBaseline()), p.getReportJson(), p.getError(), p.getCreatedAt(),
                p.getFinishedAt());
    }

    private static BigDecimal bd(Double v) {
        return v == null ? null : BigDecimal.valueOf(v).setScale(4, java.math.RoundingMode.HALF_UP);
    }

    private static Double dbl(BigDecimal v) {
        return v == null ? null : v.doubleValue();
    }
}
