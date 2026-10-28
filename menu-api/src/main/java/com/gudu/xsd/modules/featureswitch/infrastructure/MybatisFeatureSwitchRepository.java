package com.gudu.xsd.modules.featureswitch.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gudu.xsd.modules.featureswitch.domain.model.FeatureSwitch;
import com.gudu.xsd.modules.featureswitch.domain.repository.FeatureSwitchRepository;
import com.gudu.xsd.modules.featureswitch.infrastructure.mapper.FeatureSwitchLogMapper;
import com.gudu.xsd.modules.featureswitch.infrastructure.mapper.FeatureSwitchMapper;
import com.gudu.xsd.modules.featureswitch.infrastructure.po.FeatureSwitchLogPO;
import com.gudu.xsd.modules.featureswitch.infrastructure.po.FeatureSwitchPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class MybatisFeatureSwitchRepository implements FeatureSwitchRepository {

    private final FeatureSwitchMapper switchMapper;
    private final FeatureSwitchLogMapper logMapper;

    @Override
    public Optional<FeatureSwitch> load(String key) {
        FeatureSwitchPO po = switchMapper.selectById(key);
        if (po == null) return Optional.empty();
        return Optional.of(new FeatureSwitch(key, po.getEnabled() == null || po.getEnabled() != 0, po.getUpdatedBy(),
                po.getUpdatedByName(), po.getUpdatedAt(), po.getReason()));
    }

    @Override
    public void save(FeatureSwitch s) {
        FeatureSwitchPO po = new FeatureSwitchPO();
        po.setSwitchKey(s.key());
        po.setEnabled(s.enabled() ? 1 : 0);
        po.setUpdatedBy(s.updatedBy());
        po.setUpdatedByName(s.updatedByName());
        po.setUpdatedAt(s.updatedAt());
        po.setReason(s.reason());
        switchMapper.upsert(po);

        FeatureSwitchLogPO log = new FeatureSwitchLogPO();
        log.setSwitchKey(s.key());
        log.setEnabled(s.enabled() ? 1 : 0);
        log.setOperatorId(s.updatedBy());
        log.setOperatorName(s.updatedByName());
        log.setReason(s.reason());
        log.setCreateTime(s.updatedAt());
        logMapper.insert(log);
    }

    @Override
    public List<FeatureSwitch> recentChanges(String key, int limit) {
        return logMapper.selectList(new QueryWrapper<FeatureSwitchLogPO>()
                        .eq("switch_key", key)
                        .orderByDesc("id")
                        .last("LIMIT " + Math.max(1, Math.min(limit, 50))))
                .stream()
                .map(l -> new FeatureSwitch(key, l.getEnabled() != null && l.getEnabled() != 0, l.getOperatorId(),
                        l.getOperatorName(), l.getCreateTime(), l.getReason()))
                .toList();
    }
}
