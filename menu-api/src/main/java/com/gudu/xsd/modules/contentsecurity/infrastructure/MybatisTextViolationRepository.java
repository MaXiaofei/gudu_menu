package com.gudu.xsd.modules.contentsecurity.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gudu.xsd.modules.contentsecurity.domain.model.ContentPurpose;
import com.gudu.xsd.modules.contentsecurity.domain.model.Suggest;
import com.gudu.xsd.modules.contentsecurity.domain.model.TextVerdict;
import com.gudu.xsd.modules.contentsecurity.domain.model.TextViolation;
import com.gudu.xsd.modules.contentsecurity.domain.repository.TextViolationRepository;
import com.gudu.xsd.modules.contentsecurity.infrastructure.mapper.ContentTextViolationMapper;
import com.gudu.xsd.modules.contentsecurity.infrastructure.po.ContentTextViolationPO;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class MybatisTextViolationRepository implements TextViolationRepository {

    static final String PENDING = "PENDING";
    static final String CONFIRMED = "CONFIRMED";

    private final ContentTextViolationMapper mapper;

    public MybatisTextViolationRepository(ContentTextViolationMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(TextViolation v) {
        ContentTextViolationPO po = new ContentTextViolationPO();
        po.setId(v.id());
        po.setAccountId(v.accountId());
        po.setPurpose(v.purpose().name());
        po.setSuggest(v.suggest().name());
        po.setLabel(v.label());
        po.setSource(v.source().name());
        po.setExcerpt(v.excerpt());
        po.setReviewStatus(v.confirmed() ? CONFIRMED : PENDING);
        po.setReviewedBy(v.reviewedBy());
        po.setReviewedAt(v.reviewedAt());
        if (v.id() == null) {
            po.setCreatedAt(v.createdAt());
            mapper.insert(po);
            v.identify(po.getId());
        } else {
            mapper.updateById(po);
        }
    }

    @Override
    public Optional<TextViolation> findById(Long id) {
        return id == null ? Optional.empty()
                : Optional.ofNullable(mapper.selectById(id)).map(MybatisTextViolationRepository::toDomain);
    }

    @Override
    public List<TextViolation> page(Boolean confirmed, String purpose, Long accountId, int offset, int limit) {
        return mapper.selectList(filter(confirmed, purpose, accountId).orderByDesc("created_at").orderByDesc("id")
                        .last("LIMIT " + Math.max(0, offset) + ", " + Math.max(1, limit)))
                .stream().map(MybatisTextViolationRepository::toDomain).toList();
    }

    @Override
    public long count(Boolean confirmed, String purpose, Long accountId) {
        return mapper.selectCount(filter(confirmed, purpose, accountId));
    }

    private static QueryWrapper<ContentTextViolationPO> filter(Boolean confirmed, String purpose, Long accountId) {
        QueryWrapper<ContentTextViolationPO> q = new QueryWrapper<>();
        if (confirmed != null) q.eq("review_status", confirmed ? CONFIRMED : PENDING);
        if (purpose != null && !purpose.isBlank()) q.eq("purpose", purpose.trim());
        if (accountId != null) q.eq("account_id", accountId);
        return q;
    }

    static TextViolation toDomain(ContentTextViolationPO po) {
        ContentPurpose purpose;
        try {
            purpose = ContentPurpose.valueOf(po.getPurpose());
        } catch (RuntimeException e) {
            purpose = ContentPurpose.UPLOAD;
        }
        Suggest suggest;
        try {
            suggest = Suggest.valueOf(po.getSuggest());
        } catch (RuntimeException e) {
            suggest = Suggest.RISKY;
        }
        TextVerdict.Source source;
        try {
            source = TextVerdict.Source.valueOf(po.getSource());
        } catch (RuntimeException e) {
            source = TextVerdict.Source.RULE;
        }
        return TextViolation.rehydrate(po.getId(), po.getAccountId(), purpose, suggest,
                po.getLabel() == null ? 0 : po.getLabel(), source, po.getExcerpt(), po.getCreatedAt(),
                CONFIRMED.equals(po.getReviewStatus()), po.getReviewedBy(), po.getReviewedAt());
    }

    @Override
    public int reassignAccount(Long from, Long to) {
        return mapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<ContentTextViolationPO>()
                .eq("account_id", from).set("account_id", to));
    }
}
