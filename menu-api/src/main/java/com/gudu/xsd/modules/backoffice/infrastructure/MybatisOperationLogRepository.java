package com.gudu.xsd.modules.backoffice.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gudu.xsd.modules.backoffice.domain.model.OperationAction;
import com.gudu.xsd.modules.backoffice.domain.model.OperationLog;
import com.gudu.xsd.modules.backoffice.domain.model.OperationTarget;
import com.gudu.xsd.modules.backoffice.domain.repository.OperationLogRepository;
import com.gudu.xsd.modules.backoffice.infrastructure.mapper.AdminOperationLogMapper;
import com.gudu.xsd.modules.backoffice.infrastructure.po.AdminOperationLogPO;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;

@Repository
public class MybatisOperationLogRepository implements OperationLogRepository {

    private final AdminOperationLogMapper mapper;

    public MybatisOperationLogRepository(AdminOperationLogMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(OperationLog log) {
        if (log.id() != null) return; // 只追加
        AdminOperationLogPO po = new AdminOperationLogPO();
        po.setOperatorId(log.operatorId());
        po.setAction(log.action().name());
        po.setTargetType(log.targetType().name());
        po.setTargetId(log.targetId());
        po.setReason(log.reason());
        po.setDetail(log.detail());
        po.setCreatedAt(log.createdAt());
        mapper.insert(po);
        log.identify(po.getId());
    }

    @Override
    public List<OperationLog> page(OperationTarget targetType, Long targetId, int offset, int limit) {
        return mapper.selectList(filter(targetType, targetId).orderByDesc("created_at").orderByDesc("id")
                        .last("LIMIT " + Math.max(0, offset) + ", " + Math.max(1, limit)))
                .stream().map(MybatisOperationLogRepository::toDomain).filter(Objects::nonNull).toList();
    }

    @Override
    public long count(OperationTarget targetType, Long targetId) {
        return mapper.selectCount(filter(targetType, targetId));
    }

    private static QueryWrapper<AdminOperationLogPO> filter(OperationTarget targetType, Long targetId) {
        QueryWrapper<AdminOperationLogPO> q = new QueryWrapper<>();
        if (targetType != null) q.eq("target_type", targetType.name());
        if (targetId != null) q.eq("target_id", targetId);
        return q;
    }

    static OperationLog toDomain(AdminOperationLogPO po) {
        OperationAction action;
        try {
            action = OperationAction.valueOf(po.getAction());
        } catch (RuntimeException e) {
            return null;
        }
        return OperationLog.rehydrate(po.getId(), po.getOperatorId(), action, po.getTargetId(), po.getReason(),
                po.getDetail(), po.getCreatedAt());
    }
}
