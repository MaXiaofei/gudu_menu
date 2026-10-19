package com.gudu.xsd.modules.mealplan.domain.model;

import com.gudu.xsd.common.domain.AggregateRoot;
import com.gudu.xsd.common.domain.DomainException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * 一周餐程草稿（聚合根）：生成后可逐格「换一道」（本周不重复），确认后生成食集与采购清单（只能一次）。
 */
public final class MealPlanDraft extends AggregateRoot<Long> {

    public enum Status { DRAFT, APPLIED }

    private Long id;
    private final Long memberId;
    private final PlanRequest request;
    private final List<String> constraintLabels;
    private PlanCheck check;
    private List<DraftSlot> slots;
    private Status status;
    private List<Long> appliedMenuIds;
    private final LocalDateTime createdAt;

    private MealPlanDraft(Long id, Long memberId, PlanRequest request, List<String> constraintLabels, PlanCheck check,
                          List<DraftSlot> slots, Status status, List<Long> appliedMenuIds, LocalDateTime createdAt) {
        this.id = id;
        this.memberId = memberId;
        this.request = request;
        this.constraintLabels = constraintLabels == null ? List.of() : List.copyOf(constraintLabels);
        this.check = check;
        this.slots = sorted(slots);
        this.status = status == null ? Status.DRAFT : status;
        this.appliedMenuIds = appliedMenuIds == null ? List.of() : List.copyOf(appliedMenuIds);
        this.createdAt = createdAt;
    }

    public static MealPlanDraft generate(Long memberId, PlanRequest request, List<String> constraintLabels,
                                         List<PlannedSlot> planned, PlanCheck check, LocalDateTime now) {
        if (memberId == null) throw new DomainException("请先登录");
        if (planned == null || planned.isEmpty()) throw new DomainException("没有符合条件的菜，放宽一下约束试试");
        List<DraftSlot> slots = new ArrayList<>();
        for (PlannedSlot p : planned) {
            if (!request.week().contains(p.date())) throw new DomainException("排餐日期超出本周");
            slots.add(new DraftSlot(null, p.date(), p.meal(), p.dishId(), p.badges()));
        }
        return new MealPlanDraft(null, memberId, request, constraintLabels, check, slots, Status.DRAFT, null, now);
    }

    public static MealPlanDraft rehydrate(Long id, Long memberId, PlanRequest request, List<String> constraintLabels,
                                          PlanCheck check, List<DraftSlot> slots, Status status,
                                          List<Long> appliedMenuIds, LocalDateTime createdAt) {
        return new MealPlanDraft(id, memberId, request, constraintLabels, check, slots, status, appliedMenuIds, createdAt);
    }

    /** 换一道：只替换该格；新菜本周不得已出现在其它格。 */
    public void replaceDish(Long slotId, long dishId, Set<SlotBadge> badges) {
        requireDraft();
        DraftSlot target = slot(slotId);
        boolean dup = slots.stream().anyMatch(s -> !s.slotId().equals(target.slotId()) && s.dishId() == dishId);
        if (dup) throw new DomainException("这道菜本周已经排过了，换一道别的吧");
        List<DraftSlot> next = new ArrayList<>(slots.size());
        for (DraftSlot s : slots) next.add(s.slotId().equals(slotId) ? s.withDish(dishId, badges) : s);
        this.slots = sorted(next);
    }

    /** 重新校验后更新结论（换菜后调用）。 */
    public void recheck(PlanCheck newCheck) {
        this.check = newCheck;
    }

    public void markApplied(List<Long> menuIds) {
        requireDraft();
        if (menuIds == null || menuIds.isEmpty()) throw new DomainException("生成食集失败");
        this.appliedMenuIds = List.copyOf(menuIds);
        this.status = Status.APPLIED;
    }

    public DraftSlot slot(Long slotId) {
        return slots.stream().filter(s -> s.slotId() != null && s.slotId().equals(slotId)).findFirst()
                .orElseThrow(() -> new DomainException("这一格不存在"));
    }

    public boolean ownedBy(Long member) {
        return memberId != null && memberId.equals(member);
    }

    /** 持久化后回填格子 id（按日期、餐次顺序一一对应）。 */
    public void identify(Long newId, List<Long> slotIds) {
        if (this.id != null && !this.id.equals(newId)) throw new DomainException("聚合 id 不可变更");
        this.id = newId;
        if (slotIds != null && slotIds.size() == slots.size()) {
            List<DraftSlot> next = new ArrayList<>(slots.size());
            for (int i = 0; i < slots.size(); i++) next.add(slots.get(i).withId(slotIds.get(i)));
            this.slots = next;
        }
    }

    private void requireDraft() {
        if (status != Status.DRAFT) throw new DomainException("这份餐程已经生成食集了");
    }

    private static List<DraftSlot> sorted(List<DraftSlot> in) {
        if (in == null) return List.of();
        return in.stream().sorted(Comparator.comparing(DraftSlot::date).thenComparing(DraftSlot::meal)).toList();
    }

    @Override
    public Long id() {
        return id;
    }

    public Long memberId() {
        return memberId;
    }

    public PlanRequest request() {
        return request;
    }

    public List<String> constraintLabels() {
        return constraintLabels;
    }

    public PlanCheck check() {
        return check;
    }

    public List<DraftSlot> slots() {
        return slots;
    }

    public Status status() {
        return status;
    }

    public List<Long> appliedMenuIds() {
        return appliedMenuIds;
    }

    public LocalDateTime createdAt() {
        return createdAt;
    }
}
