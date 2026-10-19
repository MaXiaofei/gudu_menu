package com.gudu.xsd.modules.mealplan.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.gudu.xsd.modules.mealplan.domain.model.DraftSlot;
import com.gudu.xsd.modules.mealplan.domain.model.MealPlanDraft;
import com.gudu.xsd.modules.mealplan.domain.model.Meal;
import com.gudu.xsd.modules.mealplan.domain.model.PlanCheck;
import com.gudu.xsd.modules.mealplan.domain.model.PlanRequest;
import com.gudu.xsd.modules.mealplan.domain.model.SlotBadge;
import com.gudu.xsd.modules.mealplan.domain.repository.MealPlanDraftRepository;
import com.gudu.xsd.modules.mealplan.infrastructure.mapper.MealPlanDraftMapper;
import com.gudu.xsd.modules.mealplan.infrastructure.mapper.MealPlanSlotMapper;
import com.gudu.xsd.modules.mealplan.infrastructure.po.MealPlanDraftPO;
import com.gudu.xsd.modules.mealplan.infrastructure.po.MealPlanSlotPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class MybatisMealPlanDraftRepository implements MealPlanDraftRepository {

    static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private final MealPlanDraftMapper draftMapper;
    private final MealPlanSlotMapper slotMapper;

    @Override
    public void save(MealPlanDraft d) {
        MealPlanDraftPO po = new MealPlanDraftPO();
        po.setId(d.id());
        po.setMemberId(d.memberId());
        po.setWeekStart(d.request().week().start());
        po.setWeekEnd(d.request().week().end());
        po.setRequestJson(write(d.request()));
        po.setConstraintsJson(write(d.constraintLabels()));
        po.setCheckJson(write(d.check()));
        po.setStatus(d.status().name());
        po.setAppliedMenuIds(d.appliedMenuIds().isEmpty() ? null
                : String.join(",", d.appliedMenuIds().stream().map(String::valueOf).toList()));
        if (d.id() == null) {
            po.setCreateTime(d.createdAt());
            draftMapper.insert(po);
            List<Long> slotIds = new ArrayList<>();
            for (DraftSlot s : d.slots()) {
                MealPlanSlotPO sp = toPO(po.getId(), s);
                slotMapper.insert(sp);
                slotIds.add(sp.getId());
            }
            d.identify(po.getId(), slotIds);
            return;
        }
        draftMapper.updateById(po);
        Map<Long, MealPlanSlotPO> existing = new HashMap<>();
        slotMapper.selectList(new QueryWrapper<MealPlanSlotPO>().eq("draft_id", d.id()))
                .forEach(sp -> existing.put(sp.getId(), sp));
        for (DraftSlot s : d.slots()) {
            MealPlanSlotPO old = existing.get(s.slotId());
            MealPlanSlotPO sp = toPO(d.id(), s);
            if (old == null) {
                slotMapper.insert(sp);
            } else if (!Objects.equals(old.getDishId(), sp.getDishId()) || !Objects.equals(old.getBadges(), sp.getBadges())) {
                sp.setId(old.getId());
                slotMapper.updateById(sp);
            }
        }
    }

    @Override
    public Optional<MealPlanDraft> findById(Long id) {
        if (id == null) return Optional.empty();
        MealPlanDraftPO po = draftMapper.selectById(id);
        if (po == null) return Optional.empty();
        List<DraftSlot> slots = slotMapper.selectList(new QueryWrapper<MealPlanSlotPO>().eq("draft_id", id).orderByAsc("id"))
                .stream().map(sp -> new DraftSlot(sp.getId(), sp.getPlanDate(), Meal.of(sp.getMeal()), sp.getDishId(),
                        SlotBadge.parseCsv(sp.getBadges()))).toList();
        PlanRequest req = read(po.getRequestJson(), new TypeReference<PlanRequest>() { });
        if (req == null) return Optional.empty();
        List<String> labels = read(po.getConstraintsJson(), new TypeReference<List<String>>() { });
        PlanCheck check = read(po.getCheckJson(), new TypeReference<PlanCheck>() { });
        List<Long> menuIds = po.getAppliedMenuIds() == null || po.getAppliedMenuIds().isBlank() ? List.of()
                : Arrays.stream(po.getAppliedMenuIds().split(",")).map(String::trim).filter(s -> !s.isEmpty())
                .map(Long::valueOf).toList();
        return Optional.of(MealPlanDraft.rehydrate(po.getId(), po.getMemberId(), req, labels, check, slots,
                MealPlanDraft.Status.valueOf(po.getStatus()), menuIds, po.getCreateTime()));
    }

    private static MealPlanSlotPO toPO(Long draftId, DraftSlot s) {
        MealPlanSlotPO sp = new MealPlanSlotPO();
        sp.setDraftId(draftId);
        sp.setPlanDate(s.date());
        sp.setMeal(s.meal().name());
        sp.setDishId(s.dishId());
        sp.setBadges(SlotBadge.toCsv(s.badges()));
        return sp;
    }

    static String write(Object v) {
        try {
            return JSON.writeValueAsString(v);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("餐程序列化失败", e);
        }
    }

    static <T> T read(String json, TypeReference<T> type) {
        if (json == null || json.isBlank()) return null;
        try {
            return JSON.readValue(json, type);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
