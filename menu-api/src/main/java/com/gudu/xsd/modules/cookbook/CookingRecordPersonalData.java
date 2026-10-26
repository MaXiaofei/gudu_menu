package com.gudu.xsd.modules.cookbook;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gudu.xsd.common.privacy.PersonalDataSection;
import com.gudu.xsd.common.privacy.PersonalDataSubject;
import com.gudu.xsd.common.tenant.TenantContextHolder;
import com.gudu.xsd.modules.cookbook.mapper.CookingRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 导出我的数据（C.2）：本人档案的做菜记录（食记的数据来源）。系统模式 + 显式 member_id ∈ 本人档案。 */
@Component
@RequiredArgsConstructor
public class CookingRecordPersonalData implements PersonalDataSection {

    private final CookingRecordMapper mapper;

    @Override
    public String key() {
        return "cookingRecords";
    }

    @Override
    public int order() {
        return 40;
    }

    @Override
    public Object export(PersonalDataSubject s) {
        if (s.profileIds().isEmpty()) return List.of();
        return TenantContextHolder.callAsSystem(() -> mapper.selectList(new QueryWrapper<CookingRecord>()
                        .in("member_id", s.profileIds()).orderByAsc("cooked_at").orderByAsc("id")))
                .stream().map(r -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", r.getId());
                    m.put("familyId", r.getFamilyId());
                    m.put("profileId", r.getMemberId());
                    m.put("dishId", r.getDishId());
                    m.put("menuId", r.getMenuId());
                    m.put("cookedAt", r.getCookedAt());
                    m.put("servingFactor", r.getServingFactor());
                    m.put("note", r.getNote());
                    return m;
                }).toList();
    }
}
