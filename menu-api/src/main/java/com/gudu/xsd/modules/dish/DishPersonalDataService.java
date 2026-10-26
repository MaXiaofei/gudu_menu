package com.gudu.xsd.modules.dish;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.gudu.xsd.common.privacy.PersonalDataSection;
import com.gudu.xsd.common.privacy.PersonalDataSubject;
import com.gudu.xsd.common.tenant.TenantContextHolder;
import com.gudu.xsd.modules.dish.mapper.DishMapper;
import com.gudu.xsd.modules.dish.mapper.DishStepMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 菜谱的个人数据（mini-release-compliance C.2）：导出本人创建的菜谱（dish.created_by，V89 起记录；存量菜谱无创建人不导出）；
 * 账号合并时创建人改指目标账号。系统模式访问并显式按 created_by 过滤（跨用户所属的全部家庭）。
 */
@Service
@RequiredArgsConstructor
public class DishPersonalDataService implements PersonalDataSection {

    private final DishMapper dishMapper;
    private final DishStepMapper stepMapper;

    @Override
    public String key() {
        return "dishes";
    }

    @Override
    public int order() {
        return 30;
    }

    @Override
    public Object export(PersonalDataSubject s) {
        return TenantContextHolder.callAsSystem(() -> {
            List<Dish> dishes = dishMapper.selectList(new QueryWrapper<Dish>().eq("created_by", s.accountId())
                    .orderByAsc("id"));
            if (dishes.isEmpty()) return List.<Map<String, Object>>of();
            Map<Long, List<DishStep>> steps = stepMapper.selectList(new QueryWrapper<DishStep>()
                            .in("dish_id", dishes.stream().map(Dish::getId).toList()).orderByAsc("seq"))
                    .stream().collect(Collectors.groupingBy(DishStep::getDishId));
            return dishes.stream().map(d -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", d.getId());
                m.put("familyId", d.getFamilyId());
                m.put("name", d.getName());
                m.put("note", d.getNote());
                m.put("coverUrl", d.getCoverUrl());
                m.put("prepTime", d.getPrepTime());
                m.put("cookTime", d.getCookTime());
                m.put("difficulty", d.getDifficulty());
                m.put("sourceName", d.getSourceName());
                m.put("sourceUrl", d.getSourceUrl());
                m.put("createTime", d.getCreateTime());
                m.put("steps", steps.getOrDefault(d.getId(), List.of()).stream()
                        .map(st -> {
                            Map<String, Object> x = new LinkedHashMap<>();
                            x.put("seq", st.getSeq());
                            x.put("text", st.getText());
                            x.put("images", st.getImages());
                            return x;
                        }).toList());
                return m;
            }).toList();
        });
    }

    /** 账号合并：创建人 from → to。 */
    public int reassignCreator(Long from, Long to) {
        if (from == null || to == null) return 0;
        return TenantContextHolder.callAsSystem(() -> dishMapper.update(null,
                new UpdateWrapper<Dish>().eq("created_by", from).set("created_by", to)));
    }
}
