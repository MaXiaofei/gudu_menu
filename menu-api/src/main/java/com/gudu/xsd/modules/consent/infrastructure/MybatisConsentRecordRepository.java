package com.gudu.xsd.modules.consent.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.gudu.xsd.modules.consent.domain.model.ConsentChannel;
import com.gudu.xsd.modules.consent.domain.model.ConsentDocType;
import com.gudu.xsd.modules.consent.domain.model.ConsentRecord;
import com.gudu.xsd.modules.consent.domain.repository.ConsentRecordRepository;
import com.gudu.xsd.modules.consent.infrastructure.mapper.ConsentRecordMapper;
import com.gudu.xsd.modules.consent.infrastructure.po.ConsentRecordPO;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class MybatisConsentRecordRepository implements ConsentRecordRepository {

    private final ConsentRecordMapper mapper;

    public MybatisConsentRecordRepository(ConsentRecordMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(ConsentRecord r) {
        if (r.id() != null) return; // 只追加，不修改
        ConsentRecordPO po = new ConsentRecordPO();
        po.setAccountId(r.accountId());
        po.setDocType(r.docType().name());
        po.setDocVersion(r.docVersion());
        po.setGranted(r.granted() ? 1 : 0);
        po.setChannel(r.channel().name());
        po.setClientIp(r.clientIp());
        po.setCreatedAt(r.createdAt());
        mapper.insert(po);
        r.identify(po.getId());
    }

    @Override
    public List<ConsentRecord> listByAccount(Long accountId, int limit) {
        if (accountId == null) return List.of();
        return mapper.selectList(new QueryWrapper<ConsentRecordPO>().eq("account_id", accountId)
                        .orderByDesc("created_at").orderByDesc("id").last("LIMIT " + Math.max(1, Math.min(limit, 500))))
                .stream().map(MybatisConsentRecordRepository::toDomain).toList();
    }

    @Override
    public int reassignAccount(Long from, Long to) {
        if (from == null || to == null || from.equals(to)) return 0;
        return mapper.update(null, new UpdateWrapper<ConsentRecordPO>().eq("account_id", from).set("account_id", to));
    }

    static ConsentRecord toDomain(ConsentRecordPO po) {
        ConsentDocType type;
        try {
            type = ConsentDocType.of(po.getDocType());
        } catch (RuntimeException e) {
            type = ConsentDocType.TERMS;
        }
        return ConsentRecord.rehydrate(po.getId(), po.getAccountId(), type, po.getDocVersion(),
                po.getGranted() == null || po.getGranted() == 1, ConsentChannel.of(po.getChannel()), po.getClientIp(),
                po.getCreatedAt());
    }
}
