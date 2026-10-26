package com.gudu.xsd.modules.privacy.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.gudu.xsd.modules.privacy.domain.model.ConsentDocType;
import com.gudu.xsd.modules.privacy.domain.model.ConsentRecord;
import com.gudu.xsd.modules.privacy.domain.repository.ConsentRepository;
import com.gudu.xsd.modules.privacy.infrastructure.mapper.ConsentRecordMapper;
import com.gudu.xsd.modules.privacy.infrastructure.po.ConsentRecordPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class MybatisConsentRepository implements ConsentRepository {

    private static final java.util.Set<String> KNOWN = java.util.Arrays.stream(ConsentDocType.values())
            .map(ConsentDocType::code).collect(java.util.stream.Collectors.toUnmodifiableSet());

    private final ConsentRecordMapper mapper;

    @Override
    public void append(ConsentRecord r) {
        ConsentRecordPO po = new ConsentRecordPO();
        po.setAccountId(r.accountId());
        po.setDocType(r.docType().code());
        po.setDocVersion(r.docVersion());
        po.setGranted(r.granted() ? 1 : 0);
        po.setChannel(r.channel());
        po.setClientIp(r.clientIp());
        po.setCreatedAt(r.createdAt());
        mapper.insert(po);
    }

    @Override
    public Optional<ConsentRecord> latest(Long accountId, ConsentDocType type) {
        if (accountId == null) return Optional.empty();
        return mapper.selectList(new QueryWrapper<ConsentRecordPO>()
                        .eq("account_id", accountId).eq("doc_type", type.code())
                        .orderByDesc("created_at").orderByDesc("id").last("LIMIT 1"))
                .stream().findFirst().map(MybatisConsentRepository::toDomain);
    }

    @Override
    public List<ConsentRecord> history(Long accountId) {
        if (accountId == null) return List.of();
        return mapper.selectList(new QueryWrapper<ConsentRecordPO>().eq("account_id", accountId)
                        .orderByAsc("created_at").orderByAsc("id"))
                .stream().filter(po -> KNOWN.contains(po.getDocType())) // 其他类型（如 B.1 协议同意）由各自模型读取
                .map(MybatisConsentRepository::toDomain).toList();
    }

    @Override
    public int reassignAccount(Long from, Long to) {
        return mapper.update(null, new UpdateWrapper<ConsentRecordPO>().eq("account_id", from).set("account_id", to));
    }

    static ConsentRecord toDomain(ConsentRecordPO po) {
        return new ConsentRecord(po.getId(), po.getAccountId(), ConsentDocType.of(po.getDocType()), po.getDocVersion(),
                po.getGranted() != null && po.getGranted() == 1, po.getChannel(), po.getClientIp(), po.getCreatedAt());
    }
}
