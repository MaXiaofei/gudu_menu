package com.gudu.xsd.modules.mealplan.application;

import com.gudu.xsd.common.BizException;
import com.gudu.xsd.modules.dict.DictService;
import com.gudu.xsd.modules.mealplan.application.dto.CreateDraftCommand;
import com.gudu.xsd.modules.mealplan.application.dto.DishCardView;
import com.gudu.xsd.modules.mealplan.application.dto.MealPlanDraftView;
import com.gudu.xsd.modules.mealplan.domain.model.DishCandidate;
import com.gudu.xsd.modules.mealplan.domain.model.DraftSlot;
import com.gudu.xsd.modules.mealplan.domain.model.HouseholdProfile;
import com.gudu.xsd.modules.mealplan.domain.model.Meal;
import com.gudu.xsd.modules.mealplan.domain.model.MealPlanDraft;
import com.gudu.xsd.modules.mealplan.domain.model.PlanCheck;
import com.gudu.xsd.modules.mealplan.domain.model.PlanRequest;
import com.gudu.xsd.modules.mealplan.domain.model.PlannedSlot;
import com.gudu.xsd.modules.mealplan.domain.model.SlotBadge;
import com.gudu.xsd.modules.mealplan.domain.model.WeekRange;
import com.gudu.xsd.modules.mealplan.domain.repository.MealPlanDraftRepository;
import com.gudu.xsd.modules.mealplan.domain.service.CandidateFilter;
import com.gudu.xsd.modules.mealplan.domain.service.DishProfiler;
import com.gudu.xsd.modules.mealplan.domain.port.MealPlanModelPort;
import com.gudu.xsd.modules.mealplan.domain.service.HeuristicMealArranger;
import com.gudu.xsd.modules.mealplan.domain.service.MealPlanner;
import com.gudu.xsd.modules.member.Member;
import com.gudu.xsd.modules.menu.Menu;
import com.gudu.xsd.modules.menu.MenuDish;
import com.gudu.xsd.modules.menu.MenuSaveDTO;
import com.gudu.xsd.modules.menu.MenuService;
import com.gudu.xsd.modules.pantry.application.StockAppService;
import com.gudu.xsd.modules.pantry.application.dto.ExpiringItemView;
import com.gudu.xsd.modules.pantry.domain.model.StockLevel;
import com.gudu.xsd.modules.shopping.ShoppingItem;
import com.gudu.xsd.modules.shopping.ShoppingListVO;
import com.gudu.xsd.modules.shopping.ShoppingService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 一周餐程应用服务（mealplan 上下文）：生成草稿（规则过滤 → LLM / 启发式排布 → 校验回环）、换一道、
 * 确认后按天生成食集 + 合并采购清单（扣除余量里已有的）。
 */
@Service
public class MealPlanAppService {

    public static final int MAX_ALTERNATIVES = 3;
    static final int MIN_EXPIRING_WINDOW = 3;
    static final int MAX_EXPIRING_WINDOW = 14;
    static final DateTimeFormatter MD = DateTimeFormatter.ofPattern("M.d");

    private final MealPlanDraftRepository repository;
    private final DishCatalog catalog;
    private final DishCardAssembler cardAssembler;
    private final HouseholdAssembler household;
    private final StockAppService stockAppService;
    private final MenuService menuService;
    private final ShoppingService shoppingService;
    private final DictService dictService;
    private final Clock clock;
    private final MealPlanner planner;

    /** Spring 注入：有排餐模型端口时用 LLM 排布（失败退回启发式），否则纯启发式。 */
    @Autowired
    public MealPlanAppService(MealPlanDraftRepository repository, DishCatalog catalog, DishCardAssembler cardAssembler,
                              HouseholdAssembler household, StockAppService stockAppService, MenuService menuService,
                              ShoppingService shoppingService, DictService dictService, Clock clock,
                              ObjectProvider<MealPlanModelPort> modelPort) {
        this(repository, catalog, cardAssembler, household, stockAppService, menuService, shoppingService,
                dictService, clock, modelPort.getIfAvailable());
    }

    /** 纯启发式（单测用）。 */
    public MealPlanAppService(MealPlanDraftRepository repository, DishCatalog catalog, DishCardAssembler cardAssembler,
                              HouseholdAssembler household, StockAppService stockAppService, MenuService menuService,
                              ShoppingService shoppingService, DictService dictService, Clock clock) {
        this(repository, catalog, cardAssembler, household, stockAppService, menuService, shoppingService,
                dictService, clock, (MealPlanModelPort) null);
    }

    public MealPlanAppService(MealPlanDraftRepository repository, DishCatalog catalog, DishCardAssembler cardAssembler,
                              HouseholdAssembler household, StockAppService stockAppService, MenuService menuService,
                              ShoppingService shoppingService, DictService dictService, Clock clock,
                              MealPlanModelPort modelPort) {
        this.repository = repository;
        this.catalog = catalog;
        this.cardAssembler = cardAssembler;
        this.household = household;
        this.stockAppService = stockAppService;
        this.menuService = menuService;
        this.shoppingService = shoppingService;
        this.dictService = dictService;
        this.clock = clock;
        HeuristicMealArranger heuristic = new HeuristicMealArranger();
        this.planner = new MealPlanner(modelPort == null ? heuristic : new LlmMealArranger(modelPort, heuristic));
    }

    @Transactional(rollbackFor = Exception.class)
    public MealPlanDraftView create(Long memberId, CreateDraftCommand cmd) {
        CreateDraftCommand c = cmd == null ? new CreateDraftCommand(null, null, null, null, null, null, null) : cmd;
        LocalDate today = LocalDate.now(clock);
        WeekRange week = c.weekStart() == null ? WeekRange.defaultFor(today) : new WeekRange(c.weekStart());
        List<Member> members = household.members(c.memberIds());
        int servings = c.servings() != null ? c.servings() : Math.min(members.size(), PlanRequest.MAX_SERVINGS);
        List<Meal> meals = c.meals() == null ? null : c.meals().stream().map(Meal::of).toList();
        PlanRequest req = new PlanRequest(week, meals, servings, members.stream().map(Member::getId).toList(),
                c.weekdayMaxMinutes(), c.budgetFen(), c.preferExpiring() == null || c.preferExpiring());
        HouseholdProfile profile = household.merge(members);
        List<String> labels = new ArrayList<>(household.labels(members, servings));
        if (req.weekdayMaxMinutes() != null) labels.add("工作日 ≤" + req.weekdayMaxMinutes() + " 分钟");
        if (req.preferExpiring()) labels.add("优先用快到期");
        if (req.budgetFen() != null) labels.add("预算 ¥" + req.budgetFen() / 100);

        DishCatalog.Snapshot snap = catalog.load();
        MealPlanner.Outcome outcome = planner.plan(req, profile, snap.candidateList(), expiringIds(today, week));
        MealPlanDraft draft = MealPlanDraft.generate(memberId, req, labels, outcome.slots(), outcome.check(),
                LocalDateTime.now(clock));
        repository.save(draft);
        return view(draft, snap);
    }

    public MealPlanDraftView get(Long memberId, Long draftId) {
        return view(owned(memberId, draftId), catalog.load());
    }

    /** 换一道备选：同约束、同日时长限制、本周未出现；宝宝格只给宝宝可吃的；按 1 人份钠升序。 */
    public List<DishCardView> alternatives(Long memberId, Long draftId, Long slotId) {
        MealPlanDraft draft = owned(memberId, draftId);
        DraftSlot slot = draft.slot(slotId);
        DishCatalog.Snapshot snap = catalog.load();
        HouseholdProfile profile = household.merge(household.members(draft.request().memberIds()));
        Set<Long> inWeek = draft.slots().stream().map(DraftSlot::dishId).collect(Collectors.toSet());
        boolean needBaby = slot.badges().contains(SlotBadge.BABY);
        DishCandidate current = snap.candidates().get(slot.dishId());
        List<Long> ids = snap.candidateList().stream()
                .filter(x -> !inWeek.contains(x.dishId()))
                .filter(x -> CandidateFilter.allowed(x, profile))
                .filter(x -> CandidateFilter.fitsTime(x, slot.date(), draft.request().weekdayMaxMinutes()))
                .filter(x -> !needBaby || x.babyFriendly())
                .sorted((a, b) -> {
                    int kind = Boolean.compare(current == null || a.kind() != current.kind(),
                            current == null || b.kind() != current.kind());
                    if (kind != 0) return kind; // 同类别优先（荤换荤、素换素）
                    int s = Double.compare(a.perServing().sodiumMg(), b.perServing().sodiumMg());
                    return s != 0 ? s : Long.compare(a.dishId(), b.dishId());
                })
                .limit(MAX_ALTERNATIVES).map(DishCandidate::dishId).toList();
        return cardAssembler.list(ids, snap);
    }

    @Transactional(rollbackFor = Exception.class)
    public MealPlanDraftView replace(Long memberId, Long draftId, Long slotId, Long dishId) {
        MealPlanDraft draft = owned(memberId, draftId);
        DraftSlot slot = draft.slot(slotId);
        DishCatalog.Snapshot snap = catalog.load();
        DishCandidate cand = dishId == null ? null : snap.candidates().get(dishId);
        if (cand == null) throw new BizException("菜谱不存在");
        HouseholdProfile profile = household.merge(household.members(draft.request().memberIds()));
        if (!CandidateFilter.allowed(cand, profile)) throw new BizException("这道菜不符合家人的饮食档案");
        Set<SlotBadge> badges = EnumSet.noneOf(SlotBadge.class);
        if (slot.badges().contains(SlotBadge.BABY) && cand.babyFriendly()) badges.add(SlotBadge.BABY);
        LocalDate today = LocalDate.now(clock);
        if (draft.request().preferExpiring()
                && cand.ingredientIds().stream().anyMatch(expiringIds(today, draft.request().week())::contains)) {
            badges.add(SlotBadge.EXPIRING);
        }
        draft.replaceDish(slotId, dishId, badges);
        int rounds = draft.check() == null ? 1 : draft.check().rounds();
        PlanCheck check = MealPlanner.recheck(planned(draft), snap.candidateList(), profile, draft.request(), rounds);
        draft.recheck(check);
        repository.save(draft);
        return view(draft, snap);
    }

    /** 确认：每天生成一个食集（名称带日期）+ 一张合并采购清单（余量里有的不买）。 */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> apply(Long memberId, Long draftId) {
        MealPlanDraft draft = owned(memberId, draftId);
        if (draft.status() == MealPlanDraft.Status.APPLIED) throw new BizException("这份餐程已经生成食集了");
        Long weekdayType = firstId("menu_type", "工作日");
        Long weekendType = firstId("menu_type", "周末");
        Map<LocalDate, List<DraftSlot>> byDay = draft.slots().stream()
                .collect(Collectors.groupingBy(DraftSlot::date, LinkedHashMap::new, Collectors.toList()));
        List<Long> menuIds = new ArrayList<>();
        Set<Long> allDishIds = new LinkedHashSet<>();
        for (Map.Entry<LocalDate, List<DraftSlot>> e : byDay.entrySet()) {
            LocalDate d = e.getKey();
            Menu menu = new Menu();
            menu.setName(d.format(MD) + " " + WeekRange.weekdayLabel(d) + " · 一周餐程");
            menu.setTypeId(WeekRange.isWeekday(d) ? weekdayType : weekendType);
            menu.setTargetMemberId(memberId);
            menu.setServingCount(draft.request().servings());
            menu.setStatus("ACTIVE");
            List<MenuDish> dishes = new ArrayList<>();
            for (DraftSlot s : e.getValue()) {
                MenuDish md = new MenuDish();
                md.setDishId(s.dishId());
                md.setServingFactor(BigDecimal.ONE);
                md.setAddedByMemberId(memberId);
                md.setNote(s.meal().label() + (s.badges().contains(SlotBadge.BABY) ? " · 宝宝那份少盐软烂" : ""));
                dishes.add(md);
                allDishIds.add(s.dishId());
            }
            MenuSaveDTO dto = new MenuSaveDTO();
            dto.setMenu(menu);
            dto.setDishes(dishes);
            menuService.saveWithDishes(dto);
            menuIds.add(menu.getId());
        }
        int shoppingItems = 0;
        if (!allDishIds.isEmpty()) {
            Long listId = shoppingService.generate("dish", null, new ArrayList<>(allDishIds));
            ShoppingListVO list = shoppingService.getDetail(listId);
            List<? extends ShoppingItem> items = list == null || list.getItems() == null ? List.of() : list.getItems();
            Map<Long, StockLevel> levels = stockAppService.levelsOf(items.stream().map(ShoppingItem::getIngredientId)
                    .filter(Objects::nonNull).toList());
            for (ShoppingItem it : items) {
                StockLevel lv = levels.get(it.getIngredientId());
                if (lv != null && lv.isAvailable()) {
                    shoppingService.deleteItem(it.getId());
                } else {
                    shoppingItems++;
                }
            }
            WeekRange w = draft.request().week();
            shoppingService.renameList(listId, "一周餐程 " + w.start().format(MD) + "–" + w.end().format(MD));
        }
        draft.markApplied(menuIds);
        repository.save(draft);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("menuIds", menuIds);
        out.put("shoppingItemCount", shoppingItems);
        return out;
    }

    MealPlanDraftView view(MealPlanDraft draft, DishCatalog.Snapshot snap) {
        List<Long> dishIds = draft.slots().stream().map(DraftSlot::dishId).distinct().toList();
        Map<Long, DishCardView> cards = cardAssembler.cards(dishIds, snap);
        Map<LocalDate, List<DraftSlot>> byDay = draft.slots().stream()
                .collect(Collectors.groupingBy(DraftSlot::date, LinkedHashMap::new, Collectors.toList()));
        List<MealPlanDraftView.Day> days = new ArrayList<>();
        for (LocalDate d : draft.request().week().days()) {
            List<MealPlanDraftView.Slot> slots = byDay.getOrDefault(d, List.of()).stream()
                    .map(s -> new MealPlanDraftView.Slot(s.slotId(), s.meal().name(), cards.get(s.dishId()),
                            s.badges().stream().map(Enum::name).toList()))
                    .toList();
            days.add(new MealPlanDraftView.Day(d, WeekRange.weekdayLabel(d), WeekRange.isWeekday(d) ? null : "周末",
                    slots));
        }
        PlanCheck ck = draft.check() == null ? new PlanCheck(false, 0, List.of()) : draft.check();
        return new MealPlanDraftView(draft.id(), draft.request().week().start(), draft.request().week().end(),
                draft.constraintLabels(), new MealPlanDraftView.Check(ck.passed(), ck.rounds(), ck.notes()), days,
                summary(draft, snap, cards), draft.status().name());
    }

    private MealPlanDraftView.Summary summary(MealPlanDraft draft, DishCatalog.Snapshot snap, Map<Long, DishCardView> cards) {
        double cost = 0;
        Set<Long> ingredientIds = new LinkedHashSet<>();
        for (DraftSlot s : draft.slots()) {
            DishCandidate c = snap.candidates().get(s.dishId());
            if (c == null) continue;
            cost += c.costFen() * (double) draft.request().servings() / DishProfiler.BATCH_SERVINGS;
            ingredientIds.addAll(c.ingredientIds());
        }
        Set<String> missing = new LinkedHashSet<>();
        cards.values().forEach(card -> missing.addAll(card.coverage().missing()));
        Set<Long> expiring = expiringIds(LocalDate.now(clock), draft.request().week());
        int usesExpiring = (int) ingredientIds.stream().filter(expiring::contains).count();
        return new MealPlanDraftView.Summary((int) Math.round(cost), missing.size(), usesExpiring);
    }

    /**
     * 本周内会到期的食材：保质期落在 [周一, 周日]。周一之前就到期的（如明天到期的鲈鱼）排不进下周，
     * 应在今明两天吃掉，不计入。
     */
    private Set<Long> expiringIds(LocalDate today, WeekRange week) {
        long window = ChronoUnit.DAYS.between(today, week.end());
        int days = (int) Math.max(MIN_EXPIRING_WINDOW, Math.min(MAX_EXPIRING_WINDOW, window));
        return stockAppService.expiring(days).stream()
                .filter(e -> e.bestBefore() != null && !e.bestBefore().isBefore(week.start()))
                .map(ExpiringItemView::ingredientId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static List<PlannedSlot> planned(MealPlanDraft draft) {
        return draft.slots().stream().map(s -> new PlannedSlot(s.date(), s.meal(), s.dishId(), s.badges())).toList();
    }

    private Long firstId(String group, String name) {
        Set<Long> ids = dictService.idsByName(group, name);
        return ids.isEmpty() ? null : ids.iterator().next();
    }

    private MealPlanDraft owned(Long memberId, Long draftId) {
        MealPlanDraft d = repository.findById(draftId).orElseThrow(() -> new BizException("餐程不存在"));
        if (!d.ownedBy(memberId)) throw new BizException("餐程不存在");
        return d;
    }
}
