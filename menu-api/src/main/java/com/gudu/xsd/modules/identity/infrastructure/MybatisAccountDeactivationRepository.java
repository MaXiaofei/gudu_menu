package com.gudu.xsd.modules.identity.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gudu.xsd.modules.identity.domain.model.AccountDeactivation;
import com.gudu.xsd.modules.identity.domain.model.DeactivationStatus;
import com.gudu.xsd.modules.identity.domain.model.ErasureStepResult;
import com.gudu.xsd.modules.identity.domain.repository.AccountDeactivationRepository;
import com.gudu.xsd.modules.identity.infrastructure.mapper.AccountDeactivationMapper;
import com.gudu.xsd.modules.identity.infrastructure.po.AccountDeactivationPO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** 注销处理记录：steps 以 JSON 数组存（[{name, ok, detail}]）。 */
@Slf4j
@Repository
public class MybatisAccountDeactivationRepository implements AccountDeactivationRepository {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<List<ErasureStepResult>> STEPS = new TypeReference<>() {
    };

    private final AccountDeactivationMapper mapper;

    public MybatisAccountDeactivationRepository(AccountDeactivationMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(AccountDeactivation d) {
        AccountDeactivationPO po = new AccountDeactivationPO();
        po.setId(d.id());
        po.setAccountId(d.accountId());
        po.setRequestedAt(d.requestedAt());
        po.setStatus(d.status().name());
        po.setSteps(writeSteps(d.steps()));
        po.setAttempts(d.attempts());
        po.setLastError(d.lastError() == null || d.lastError().length() <= 500 ? d.lastError()
                : d.lastError().substring(0, 500));
        po.setCompletedAt(d.completedAt());
        if (d.id() == null) {
            mapper.insert(po);
            d.identify(po.getId());
        } else {
            mapper.updateById(po);
        }
    }

    @Override
    public Optional<AccountDeactivation> findById(Long id) {
        return id == null ? Optional.empty() : Optional.ofNullable(mapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public Optional<AccountDeactivation> latestByAccount(Long accountId) {
        if (accountId == null) return Optional.empty();
        return Optional.ofNullable(mapper.selectOne(new QueryWrapper<AccountDeactivationPO>()
                .eq("account_id", accountId).orderByDesc("id").last("LIMIT 1"))).map(this::toDomain);
    }

    @Override
    public List<AccountDeactivation> page(DeactivationStatus status, LocalDateTime overdueBefore, int offset,
                                          int limit) {
        return mapper.selectList(filter(status, overdueBefore).orderByDesc("requested_at").orderByDesc("id")
                        .last("LIMIT " + Math.max(0, offset) + ", " + Math.max(1, limit)))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public long count(DeactivationStatus status, LocalDateTime overdueBefore) {
        return mapper.selectCount(filter(status, overdueBefore));
    }

    private static QueryWrapper<AccountDeactivationPO> filter(DeactivationStatus status, LocalDateTime overdueBefore) {
        QueryWrapper<AccountDeactivationPO> q = new QueryWrapper<>();
        if (status != null) q.eq("status", status.name());
        if (overdueBefore != null) q.ne("status", DeactivationStatus.DONE.name()).lt("requested_at", overdueBefore);
        return q;
    }

    private AccountDeactivation toDomain(AccountDeactivationPO po) {
        return AccountDeactivation.rehydrate(po.getId(), po.getAccountId(), po.getRequestedAt(),
                DeactivationStatus.of(po.getStatus()), readSteps(po.getSteps()),
                po.getAttempts() == null ? 0 : po.getAttempts(), po.getLastError(), po.getCompletedAt());
    }

    static String writeSteps(List<ErasureStepResult> steps) {
        try {
            String s = JSON.writeValueAsString(steps);
            return s.length() <= 2000 ? s : s.substring(0, 2000);
        } catch (Exception e) {
            return "[]";
        }
    }

    static List<ErasureStepResult> readSteps(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return JSON.readValue(json, STEPS);
        } catch (Exception e) {
            log.warn("注销步骤 JSON 解析失败：{}", e.getMessage());
            return List.of();
        }
    }
}
