package com.gudu.xsd.modules.community.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gudu.xsd.modules.community.domain.model.CommunitySwitch;
import com.gudu.xsd.modules.community.domain.repository.CommunitySwitchRepository;
import com.gudu.xsd.modules.community.infrastructure.mapper.SysSwitchLogMapper;
import com.gudu.xsd.modules.community.infrastructure.mapper.SysSwitchMapper;
import com.gudu.xsd.modules.community.infrastructure.po.SysSwitchLogPO;
import com.gudu.xsd.modules.community.infrastructure.po.SysSwitchPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class MybatisCommunitySwitchRepository implements CommunitySwitchRepository {

    private final SysSwitchMapper switchMapper;
    private final SysSwitchLogMapper logMapper;

    @Override
    public Optional<CommunitySwitch> load() {
        SysSwitchPO po = switchMapper.selectById(CommunitySwitch.KEY);
        if (po == null) return Optional.empty();
        return Optional.of(new CommunitySwitch(po.getEnabled() == null || po.getEnabled() != 0, po.getUpdatedBy(),
                po.getUpdatedByName(), po.getUpdatedAt(), po.getReason()));
    }

    @Override
    public void save(CommunitySwitch s) {
        SysSwitchPO po = new SysSwitchPO();
        po.setSwitchKey(CommunitySwitch.KEY);
        po.setEnabled(s.enabled() ? 1 : 0);
        po.setUpdatedBy(s.updatedBy());
        po.setUpdatedByName(s.updatedByName());
        po.setUpdatedAt(s.updatedAt());
        po.setReason(s.reason());
        switchMapper.upsert(po);

        SysSwitchLogPO log = new SysSwitchLogPO();
        log.setSwitchKey(CommunitySwitch.KEY);
        log.setEnabled(s.enabled() ? 1 : 0);
        log.setOperatorId(s.updatedBy());
        log.setOperatorName(s.updatedByName());
        log.setReason(s.reason());
        log.setCreateTime(s.updatedAt());
        logMapper.insert(log);
    }

    @Override
    public List<CommunitySwitch> recentChanges(int limit) {
        return logMapper.selectList(new QueryWrapper<SysSwitchLogPO>()
                        .eq("switch_key", CommunitySwitch.KEY)
                        .orderByDesc("id")
                        .last("LIMIT " + Math.max(1, Math.min(limit, 50))))
                .stream()
                .map(l -> new CommunitySwitch(l.getEnabled() != null && l.getEnabled() != 0, l.getOperatorId(),
                        l.getOperatorName(), l.getCreateTime(), l.getReason()))
                .toList();
    }
}
