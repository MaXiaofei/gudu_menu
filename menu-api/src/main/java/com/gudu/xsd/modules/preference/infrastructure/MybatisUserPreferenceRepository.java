package com.gudu.xsd.modules.preference.infrastructure;

import com.gudu.xsd.modules.preference.domain.model.UserPreference;
import com.gudu.xsd.modules.preference.domain.repository.UserPreferenceRepository;
import com.gudu.xsd.modules.preference.infrastructure.mapper.UserPreferenceMapper;
import com.gudu.xsd.modules.preference.infrastructure.po.UserPreferencePO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class MybatisUserPreferenceRepository implements UserPreferenceRepository {

    private final UserPreferenceMapper mapper;

    @Override
    public Optional<UserPreference> findByMemberId(Long memberId) {
        if (memberId == null) return Optional.empty();
        return Optional.ofNullable(mapper.selectById(memberId))
                .map(po -> UserPreference.rehydrate(po.getMemberId(),
                        po.getPersonalizedRecommend() == null || po.getPersonalizedRecommend() != 0));
    }

    @Override
    public int deleteByMemberId(Long memberId) {
        return memberId == null ? 0 : mapper.deleteById(memberId);
    }

    @Override
    public void save(UserPreference p) {
        UserPreferencePO po = new UserPreferencePO();
        po.setMemberId(p.memberId());
        po.setPersonalizedRecommend(p.personalizedRecommend() ? 1 : 0);
        if (mapper.updateById(po) == 0) {
            mapper.insert(po);
        }
    }

    @Override
    public int reassignAccount(Long from, Long to) {
        if (from == null || to == null || from.equals(to) || mapper.selectById(from) == null) return 0;
        if (mapper.selectById(to) == null) {
            return mapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<UserPreferencePO>()
                    .eq("member_id", from).set("member_id", to));
        }
        return mapper.deleteById(from);
    }
}
