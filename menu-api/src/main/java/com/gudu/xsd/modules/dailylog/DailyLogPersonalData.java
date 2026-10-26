package com.gudu.xsd.modules.dailylog;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gudu.xsd.common.privacy.PersonalDataSection;
import com.gudu.xsd.common.privacy.PersonalDataSubject;
import com.gudu.xsd.common.tenant.TenantContextHolder;
import com.gudu.xsd.modules.dailylog.mapper.DailyLogItemMapper;
import com.gudu.xsd.modules.dailylog.mapper.DailyLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 导出我的数据（C.2）：本人档案的饮食日志与摄入项。系统模式 + 显式 member_id ∈ 本人档案。 */
@Component
@RequiredArgsConstructor
public class DailyLogPersonalData implements PersonalDataSection {

    private final DailyLogMapper logMapper;
    private final DailyLogItemMapper itemMapper;

    @Override
    public String key() {
        return "dailyLogs";
    }

    @Override
    public int order() {
        return 50;
    }

    @Override
    public Object export(PersonalDataSubject s) {
        if (s.profileIds().isEmpty()) return List.of();
        return TenantContextHolder.callAsSystem(() -> {
            List<DailyLog> logs = logMapper.selectList(new QueryWrapper<DailyLog>()
                    .in("member_id", s.profileIds()).orderByAsc("date").orderByAsc("id"));
            if (logs.isEmpty()) return List.<Map<String, Object>>of();
            Map<Long, List<DailyLogItem>> items = itemMapper.selectList(new QueryWrapper<DailyLogItem>()
                            .in("log_id", logs.stream().map(DailyLog::getId).toList()).orderByAsc("id"))
                    .stream().collect(Collectors.groupingBy(DailyLogItem::getLogId));
            return logs.stream().map(l -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", l.getId());
                m.put("familyId", l.getFamilyId());
                m.put("profileId", l.getMemberId());
                m.put("date", l.getDate());
                m.put("note", l.getNote());
                m.put("items", items.getOrDefault(l.getId(), List.of()).stream().map(i -> {
                    Map<String, Object> x = new LinkedHashMap<>();
                    x.put("dishId", i.getDishId());
                    x.put("ingredientId", i.getIngredientId());
                    x.put("amount", i.getAmount());
                    x.put("servingFactor", i.getServingFactor());
                    return x;
                }).toList());
                return m;
            }).toList();
        });
    }
}
