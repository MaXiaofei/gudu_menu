package com.gudu.xsd.modules.agent.application.kitchen;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gudu.xsd.common.PageQuery;
import com.gudu.xsd.common.domain.DomainException;
import com.gudu.xsd.modules.agent.domain.port.Kitchen;
import com.gudu.xsd.modules.dish.Dish;
import com.gudu.xsd.modules.dish.DishIngredient;
import com.gudu.xsd.modules.dish.DishSearchDTO;
import com.gudu.xsd.modules.dish.DishService;
import com.gudu.xsd.modules.dish.DishStep;
import com.gudu.xsd.modules.member.Member;
import com.gudu.xsd.modules.menu.CookMaterialsVO;
import com.gudu.xsd.modules.menu.Menu;
import com.gudu.xsd.modules.menu.MenuDish;
import com.gudu.xsd.modules.menu.MenuPageQuery;
import com.gudu.xsd.modules.menu.MenuSaveDTO;
import com.gudu.xsd.modules.menu.MenuService;
import com.gudu.xsd.modules.menu.application.CookDishResult;
import com.gudu.xsd.modules.nutrition.Ingredient;
import com.gudu.xsd.modules.pantry.PantryGroupedVO;
import com.gudu.xsd.modules.pantry.StockLog;
import com.gudu.xsd.modules.pantry.application.dto.BestBeforeUpdate;
import com.gudu.xsd.modules.pantry.application.dto.CoverageView;
import com.gudu.xsd.modules.pantry.application.dto.ExpiringItemView;
import com.gudu.xsd.modules.pantry.application.dto.StockShelfView;
import com.gudu.xsd.modules.pantry.domain.model.StockLevel;
import com.gudu.xsd.modules.recommend.application.dto.DishCard;
import com.gudu.xsd.modules.recommend.application.dto.HomeRecommendView;
import com.gudu.xsd.modules.shopping.ShoppingItemVO;
import com.gudu.xsd.modules.shopping.ShoppingList;
import com.gudu.xsd.modules.shopping.ShoppingListVO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 线上厨房：委托各业务上下文的应用服务（不直连他模块 Mapper）。
 *
 * <p>当前为单家庭部署（multi-tenant-family 落地前）：「本家庭」= 本库全部数据，归属校验退化为存在性校验；
 * 多租户上线后在本类按 familyId 过滤并对别家数据返回 FORBIDDEN。
 */
class LiveKitchen implements Kitchen {

    static final int MENU_SCAN = 30;
    static final int POOL = 200;

    private final LiveKitchenFactory f;
    private final Long memberId;
    private List<MemberInfo> membersCache;

    LiveKitchen(LiveKitchenFactory f, Long memberId) {
        this.f = f;
        this.memberId = memberId;
    }

    @Override
    public LocalDateTime now() {
        return LocalDateTime.now(f.clock);
    }

    // ================================================================= 成员

    @Override
    public List<MemberInfo> members() {
        if (membersCache == null) {
            membersCache = f.memberService.household(memberId).stream().map(this::member).toList();
        }
        return membersCache;
    }

    @SuppressWarnings("unchecked")
    private MemberInfo member(Member m) {
        List<String> tags = new ArrayList<>();
        Integer age = null;
        Map<String, Object> hp = m.getHealthProfile();
        if (hp != null) {
            Object aud = hp.get("audiences");
            if (aud instanceof Collection<?> c) c.forEach(x -> tags.add(String.valueOf(x)));
            Object a = hp.get("age");
            if (a instanceof Number n) age = n.intValue();
        }
        return new MemberInfo(m.getId(), m.getName(), m.getId().equals(memberId), age, tags, m.getAvatarUrl());
    }

    // ================================================================= 菜谱

    @Override
    public List<DishInfo> searchDishes(DishQuery q) {
        List<Dish> pool = pool(q);
        if (pool.isEmpty()) return List.of();
        f.dishService.decorateCards(pool);
        List<Dish> filtered = pool.stream()
                .filter(d -> tagsOf(d).stream().noneMatch(q.excludeTags()::contains))
                .filter(d -> tagsOf(d).containsAll(q.tags()))
                .filter(d -> q.maxMinutes() == null || minutes(d) == null || minutes(d) <= q.maxMinutes())
                .toList();
        if (filtered.isEmpty()) return List.of();
        List<Long> ids = filtered.stream().map(Dish::getId).toList();
        Map<Long, CoverageView> cov = f.coverage.coverageOf(ids);
        Set<Long> expiringIngs = f.stockAppService.expiring(3).stream().map(ExpiringItemView::ingredientId)
                .collect(Collectors.toSet());
        Map<Long, List<DishIngredient>> ingByDish = f.dishService.ingredientsOf(ids).stream()
                .collect(Collectors.groupingBy(DishIngredient::getDishId));
        List<Dish> ranked = filtered.stream().sorted(Comparator.comparingDouble((Dish d) -> -score(d, q, cov,
                ingByDish.getOrDefault(d.getId(), List.of()), expiringIngs))).limit(q.limit()).toList();
        return toInfos(ranked, cov, ingByDish, false);
    }

    private List<Dish> pool(DishQuery q) {
        if (!q.ingredients().isEmpty()) {
            List<Long> ingIds = f.ingredientService.list(new QueryWrapper<Ingredient>().select("id")
                    .in("name", q.ingredients())).stream().map(Ingredient::getId).toList();
            if (!ingIds.isEmpty()) {
                List<Long> dishIds = f.dishService.dishIdsUsingIngredients(ingIds).stream().distinct().limit(POOL).toList();
                if (!dishIds.isEmpty()) return new ArrayList<>(f.dishService.listByIds(dishIds));
            }
        }
        String kw = keyword(q.query());
        if (kw != null) {
            DishSearchDTO dto = new DishSearchDTO();
            dto.setKeyword(kw);
            dto.setMaxMinutes(q.maxMinutes());
            List<Dish> hit = f.dishService.searchPool(dto);
            if (!hit.isEmpty()) return new ArrayList<>(hit);
        }
        DishSearchDTO all = new DishSearchDTO();
        all.setMaxMinutes(q.maxMinutes());
        return new ArrayList<>(f.dishService.searchPool(all));
    }

    /** 只有像菜名的短词才当关键词（「冰箱里的能做什么」不当关键词）。 */
    private static String keyword(String query) {
        if (query == null) return null;
        String k = query.trim();
        if (k.isEmpty() || k.length() > 8 || k.matches(".*(什么|吗|呢|怎么|能|帮我|推荐|吃).*")) return null;
        return k;
    }

    private static double score(Dish d, DishQuery q, Map<Long, CoverageView> cov, List<DishIngredient> ings,
                                Set<Long> expiring) {
        CoverageView c = cov.get(d.getId());
        double s = c == null ? 0 : c.ratio() * 2;
        for (String t : tagsOf(d)) if (q.preferTags().contains(t)) s += 0.5;
        for (DishIngredient i : ings) if (expiring.contains(i.getIngredientId())) s += 1;
        return s;
    }

    @Override
    public Lookup<DishInfo> findDish(String nameOrId) {
        if (nameOrId == null || nameOrId.isBlank()) return Lookup.notFound();
        String ref = nameOrId.trim();
        Dish d = null;
        if (ref.matches("\\d+")) d = f.dishService.getById(Long.parseLong(ref));
        if (d == null) {
            List<Dish> exact = f.dishService.list(new QueryWrapper<Dish>().eq("name", ref).orderByAsc("id").last("LIMIT 1"));
            if (!exact.isEmpty()) d = exact.get(0);
        }
        if (d == null) {
            List<Dish> like = f.dishService.list(new QueryWrapper<Dish>().like("name", ref).orderByAsc("id").last("LIMIT 6"));
            if (like.size() == 1) d = like.get(0);
            else if (like.size() > 1) return Lookup.ambiguous(toInfos(like, Map.of(), Map.of(), false));
        }
        if (d == null) return Lookup.notFound();
        return Lookup.found(detail(d.getId()));
    }

    private DishInfo detail(Long id) {
        DishService.DishDetail det = f.dishService.detail(id);
        List<Dish> one = new ArrayList<>(List.of(det.dish()));
        f.dishService.decorateCards(one);
        Map<Long, CoverageView> cov = f.coverage.coverageOf(List.of(id));
        Map<Long, List<DishIngredient>> ing = Map.of(id, det.ingredients());
        DishInfo base = toInfos(one, cov, ing, true).get(0);
        List<String> steps = det.steps() == null ? List.of()
                : det.steps().stream().sorted(Comparator.comparing(s -> s.getSeq() == null ? 0 : s.getSeq()))
                .map(DishStep::getText).filter(Objects::nonNull).toList();
        return new DishInfo(base.id(), base.name(), base.coverUrl(), base.minutes(), base.difficulty(), base.tags(),
                base.ingredients(), base.sodiumMg(), base.coverage(), steps, det.dish().getNote());
    }

    @Override
    public List<DishInfo> recommendTonight(int n, Set<String> excludeTags) {
        HomeRecommendView home = f.homeRecommend.home(memberId);
        List<Long> ids = new ArrayList<>();
        for (List<DishCard> part : List.of(home.expiringPicks(), home.frequent(), home.seasonal())) {
            for (DishCard c : part) if (!ids.contains(c.dishId())) ids.add(c.dishId());
        }
        if (ids.isEmpty()) return searchDishes(new DishQuery(null, List.of(), List.of(), excludeTags, Set.of(), null, n));
        List<Dish> dishes = new ArrayList<>(f.dishService.listByIds(ids));
        f.dishService.decorateCards(dishes);
        Map<Long, Dish> byId = dishes.stream().collect(Collectors.toMap(Dish::getId, Function.identity(), (a, b) -> a));
        // 完整菜谱（有封面 + 有步骤）排前，顺序内保持「临期 → 常做 → 应季」
        Set<Long> withSteps = f.dishService.dishIdsWithSteps(ids);
        List<Dish> ordered = ids.stream().map(byId::get).filter(Objects::nonNull)
                .filter(d -> tagsOf(d).stream().noneMatch(excludeTags::contains))
                .sorted(java.util.Comparator.comparing((Dish d) -> !(withSteps.contains(d.getId())
                        && d.getCoverUrl() != null && !d.getCoverUrl().isBlank())))
                .limit(n).toList();
        List<Long> keep = ordered.stream().map(Dish::getId).toList();
        Map<Long, List<DishIngredient>> ing = f.dishService.ingredientsOf(keep).stream()
                .collect(Collectors.groupingBy(DishIngredient::getDishId));
        return toInfos(ordered, f.coverage.coverageOf(keep), ing, false);
    }

    private List<DishInfo> toInfos(List<Dish> dishes, Map<Long, CoverageView> cov,
                                   Map<Long, List<DishIngredient>> ingByDish, boolean withAmounts) {
        Set<Long> ingIds = ingByDish.values().stream().flatMap(List::stream).map(DishIngredient::getIngredientId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Ingredient> ingMap = ingIds.isEmpty() ? Map.of() : f.ingredientService.listByIds(ingIds).stream()
                .collect(Collectors.toMap(Ingredient::getId, Function.identity(), (a, b) -> a));
        List<DishInfo> out = new ArrayList<>();
        for (Dish d : dishes) {
            CoverageView c = cov.get(d.getId());
            List<IngredientLine> lines = new ArrayList<>();
            for (DishIngredient di : ingByDish.getOrDefault(d.getId(), List.of())) {
                Ingredient ing = di.getIngredientId() == null ? null : ingMap.get(di.getIngredientId());
                String name = ing != null ? ing.getName() : di.getIngredientName();
                if (name == null) continue;
                String amount = !withAmounts || di.getAmount() == null ? null
                        : di.getAmount().stripTrailingZeros().toPlainString() + (di.getUnitName() == null ? "" : di.getUnitName());
                lines.add(new IngredientLine(name, amount, false, ing == null ? di.getIconKey() : ing.getIconKey()));
            }
            out.add(new DishInfo(d.getId(), d.getName(), d.getCoverUrl(), minutes(d), difficulty(d.getDifficulty()),
                    tagsOf(d), lines, null,
                    c == null ? null : new Coverage(c.enough(), c.total(),
                            c.missing().stream().map(CoverageView.Missing::name).toList()),
                    List.of(), null));
        }
        return out;
    }

    private static List<String> tagsOf(Dish d) {
        List<String> t = new ArrayList<>();
        if (d.getTagNames() != null) t.addAll(d.getTagNames());
        if (d.getCategoryNames() != null) t.addAll(d.getCategoryNames());
        return t;
    }

    private static Integer minutes(Dish d) {
        if (d.getPrepTime() == null && d.getCookTime() == null) return null;
        return (d.getPrepTime() == null ? 0 : d.getPrepTime()) + (d.getCookTime() == null ? 0 : d.getCookTime());
    }

    private static String difficulty(Integer level) {
        if (level == null) return null;
        return level <= 1 ? "简单" : level == 2 ? "中等" : "较难";
    }

    // ================================================================= 余量 / 采购

    @Override
    public List<PantryItem> pantry() {
        return f.pantryService.grouped().getItems().stream()
                .map(i -> new PantryItem(i.getIngredientId(), i.getIngredientName(), i.getIconKey(), level(i.getLevel()),
                        i.getDaysLeft(), null, i.getBestBefore(), null))
                .toList();
    }

    private static String level(String l) {
        return "NONE".equals(l) ? "OUT" : l;
    }

    @Override
    public List<ShoppingItem> shoppingList() {
        List<ShoppingList> latest = f.shoppingService.page(page(1)).getRecords();
        if (latest.isEmpty()) return List.of();
        ShoppingListVO vo = f.shoppingService.getDetail(latest.get(0).getId());
        if (vo == null || vo.getItems() == null) return List.of();
        List<ShoppingItem> out = new ArrayList<>();
        for (ShoppingItemVO it : vo.getItems()) {
            String name = it.getIngredientName() != null ? it.getIngredientName() : it.getCustomName();
            if (name == null) continue;
            out.add(new ShoppingItem(name,
                    it.getPurchaseAmount() == null ? null : it.getPurchaseAmount().stripTrailingZeros().toPlainString(),
                    it.getPurchaseUnitName(), latest.get(0).getName(), Integer.valueOf(1).equals(it.getPurchased())));
        }
        return out;
    }

    // ================================================================= 食集

    @Override
    public List<MenuInfo> menus() {
        MenuPageQuery q = new MenuPageQuery();
        q.setPageNum(1);
        q.setPageSize(MENU_SCAN);
        List<MenuInfo> out = new ArrayList<>();
        for (Menu m : f.menuService.page(q).getRecords()) {
            boolean done = "DONE".equalsIgnoreCase(m.getStatus());
            List<String> dishNames = done ? List.of() : f.menuService.detail(m.getId()).dishes().stream()
                    .map(MenuService.MenuDishVO::dishName).filter(Objects::nonNull).toList();
            LocalDate date = m.getCreateTime() == null ? null : m.getCreateTime().toLocalDate();
            out.add(new MenuInfo(m.getId(), m.getName(), date, m.getServingCount() == null ? 1 : m.getServingCount(),
                    done ? "DONE" : "ONGOING", dishNames, m.getCovers(), null));
        }
        return out;
    }

    @Override
    public Lookup<MenuInfo> findMenu(String nameOrId) {
        if (nameOrId == null || nameOrId.isBlank()) return Lookup.notFound();
        String ref = nameOrId.trim();
        List<MenuInfo> all = menus();
        if (ref.matches("\\d+")) {
            long id = Long.parseLong(ref);
            for (MenuInfo m : all) if (m.id() == id) return Lookup.found(m);
            Menu menu = f.menuService.getById(id);
            return menu == null ? Lookup.notFound() : Lookup.forbidden();
        }
        for (MenuInfo m : all) if (m.name().equals(ref)) return Lookup.found(m);
        List<MenuInfo> like = all.stream().filter(m -> !"DONE".equals(m.status()))
                .filter(m -> m.name().contains(ref) || ref.contains(m.name())).toList();
        if (like.size() == 1) return Lookup.found(like.get(0));
        if (like.size() > 1) return Lookup.ambiguous(like);
        return Lookup.notFound();
    }

    // ================================================================= 写

    @Override
    public WriteResult createMenu(String name, LocalDate date, int servings, List<Long> dishIds) {
        Menu m = new Menu();
        m.setName(name);
        m.setServingCount(servings);
        m.setStatus("ACTIVE");
        MenuSaveDTO dto = new MenuSaveDTO();
        dto.setMenu(m);
        List<MenuDish> rels = new ArrayList<>();
        for (Long id : dishIds) {
            MenuDish md = new MenuDish();
            md.setDishId(id);
            md.setServingFactor(BigDecimal.ONE);
            md.setAddedByMemberId(memberId);
            rels.add(md);
        }
        dto.setDishes(rels);
        f.menuService.saveWithDishes(dto);
        return new WriteResult(Map.of("menuId", m.getId(), "name", name, "added", dishIds.size()), "menu.delete",
                Map.of("menuId", m.getId()));
    }

    @Override
    public WriteResult addDishesToMenu(long menuId, List<Long> dishIds) {
        List<Long> added = f.menuService.addDishes(menuId, dishIds, memberId);
        return new WriteResult(Map.of("menuId", menuId, "added", added.size()), "menu.removeDishes",
                Map.of("menuId", menuId, "dishIds", added));
    }

    @Override
    public WriteResult removeDishesFromMenu(long menuId, List<Long> dishIds) {
        List<Long> removed = new ArrayList<>();
        for (Long id : dishIds) if (f.menuService.removeDish(menuId, id)) removed.add(id);
        return new WriteResult(Map.of("menuId", menuId, "removed", removed.size()), "menu.addDishes",
                Map.of("menuId", menuId, "dishIds", removed));
    }

    @Override
    public WriteResult addShopping(List<ItemLine> items) {
        List<ShoppingList> latest = f.shoppingService.page(page(1)).getRecords();
        Long listId = latest.isEmpty() ? f.shoppingService.createEmpty() : latest.get(0).getId();
        List<Long> itemIds = new ArrayList<>();
        for (ItemLine it : items) {
            itemIds.add(f.shoppingService.addItemCustom(listId, it.name(), decimal(it.amount()), null, null));
        }
        return new WriteResult(Map.of("listId", listId, "added", itemIds.size()), "shopping.delete",
                Map.of("itemIds", itemIds));
    }

    @Override
    public CookPreview previewCook(long dishId, int servings, List<ItemLine> overrides) {
        CookMaterialsVO vo = f.cookAppService.dishMaterials(dishId, BigDecimal.valueOf(servings));
        DishInfo dish = detail(dishId);
        List<CookLine> lines = new ArrayList<>();
        for (CookMaterialsVO.Item it : vo.items()) {
            String delta = it.usageTexts() == null || it.usageTexts().isEmpty() ? "适量" : String.join("；", it.usageTexts());
            String forced = null;
            for (ItemLine o : overrides) {
                if (!o.name().equals(it.ingredientName())) continue;
                if ("ALL".equals(o.amount()) || "0".equals(o.amount())) forced = o.amount();
                else delta = o.amount() + (o.unit() == null ? "" : o.unit());
            }
            if ("0".equals(forced)) {
                lines.add(new CookLine(it.ingredientId(), it.ingredientName(), it.iconKey(), "不扣", "不变", "SKIP"));
            } else if ("ALL".equals(forced) && !"NONE".equals(it.level())) {
                lines.add(new CookLine(it.ingredientId(), it.ingredientName(), it.iconKey(), "-" + delta, "用完了", "USED_UP"));
            } else if (it.isCondiment()) {
                lines.add(new CookLine(it.ingredientId(), it.ingredientName(), it.iconKey(), delta, "调料不计量", "SKIP"));
            } else if ("ENOUGH".equals(it.level())) {
                lines.add(new CookLine(it.ingredientId(), it.ingredientName(), it.iconKey(), "-" + delta, "还剩一些", "LEFT"));
            } else if ("LOW".equals(it.level())) {
                lines.add(new CookLine(it.ingredientId(), it.ingredientName(), it.iconKey(), "-" + delta, "用完了", "USED_UP"));
            } else {
                lines.add(new CookLine(it.ingredientId(), it.ingredientName(), it.iconKey(), delta, "家里没有记录", "SKIP"));
            }
        }
        return new CookPreview(dish, servings, lines);
    }

    @Override
    public WriteResult markCooked(long dishId, int servings, List<Long> eaterIds, LocalDate date,
                                  List<ItemLine> overrides) {
        CookPreview p = previewCook(dishId, servings, overrides);
        List<Long> usedUp = p.lines().stream().filter(l -> "USED_UP".equals(l.status())).map(CookLine::ingredientId).toList();
        List<Long> partial = p.lines().stream().filter(l -> "LEFT".equals(l.status())).map(CookLine::ingredientId).toList();
        Map<String, Object> snapshot = snapshot(concat(usedUp, partial));
        CookDishResult r = f.cookAppService.cookDish(dishId, memberId, BigDecimal.valueOf(servings), usedUp, partial,
                "咕嘟 Agent");
        Map<String, Object> plan = new LinkedHashMap<>(snapshot);
        plan.put("recordIds", List.of(r.recordId()));
        return new WriteResult(Map.of("recordId", r.recordId(), "dishId", dishId), "cooked.restore", plan);
    }

    @Override
    public WriteResult addPantry(List<PantryLine> items) {
        List<Long> resolved = new ArrayList<>();
        Map<String, Object> before = new LinkedHashMap<>();
        for (PantryLine it : items) {
            List<Ingredient> existing = f.ingredientService.list(new QueryWrapper<Ingredient>().eq("name", it.name())
                    .last("LIMIT 1"));
            if (!existing.isEmpty()) before.putAll(snapshot(List.of(existing.get(0).getId())));
            Long id = f.pantryService.manualAdd(null, it.name(), StockLevel.ENOUGH.name(), "咕嘟入库");
            if (it.bestBefore() != null) f.stockAppService.changeBestBefore(id, it.bestBefore());
            resolved.add(id);
        }
        Map<String, Object> plan = new LinkedHashMap<>(before);
        plan.put("ingredientIds", resolved);
        return new WriteResult(Map.of("added", resolved.size()), "pantry.restore", plan);
    }

    /** 档位与保质期快照：levels[id]=ENOUGH|LOW|NONE|null（null=原先没建档）。 */
    private Map<String, Object> snapshot(List<Long> ids) {
        Map<Long, StockLevel> levels = f.stockAppService.levelsOf(ids);
        Map<Long, StockShelfView> shelf = f.stockAppService.shelfViewsOf(ids);
        Map<String, Object> lv = new HashMap<>();
        Map<String, Object> bb = new HashMap<>();
        for (Long id : ids) {
            StockLevel l = levels.get(id);
            lv.put(String.valueOf(id), l == null ? "" : l.name());
            StockShelfView s = shelf.get(id);
            if (s != null && s.bestBefore() != null) bb.put(String.valueOf(id), s.bestBefore().toString());
        }
        return Map.of("levels", lv, "bestBefore", bb);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void undo(String kind, Map<String, Object> plan) {
        switch (kind) {
            case "menu.delete" -> f.menuService.removeById(((Number) plan.get("menuId")).longValue());
            case "menu.removeDishes" -> {
                long menuId = ((Number) plan.get("menuId")).longValue();
                for (Long id : longs(plan.get("dishIds"))) f.menuService.removeDish(menuId, id);
            }
            case "menu.addDishes" -> f.menuService.addDishes(((Number) plan.get("menuId")).longValue(),
                    longs(plan.get("dishIds")), memberId);
            case "shopping.delete" -> longs(plan.get("itemIds")).forEach(f.shoppingService::deleteItem);
            case "cooked.restore" -> {
                restoreLevels((Map<String, Object>) plan.get("levels"), (Map<String, Object>) plan.get("bestBefore"));
                f.cookingRecords.revoke(longs(plan.get("recordIds")));
            }
            case "pantry.restore" -> {
                Map<String, Object> levels = new HashMap<>((Map<String, Object>) plan.getOrDefault("levels", Map.of()));
                for (Long id : longs(plan.get("ingredientIds"))) levels.putIfAbsent(String.valueOf(id), "");
                restoreLevels(levels, (Map<String, Object>) plan.getOrDefault("bestBefore", Map.of()));
            }
            default -> throw new DomainException("不支持的撤销：" + kind);
        }
    }

    private void restoreLevels(Map<String, Object> levels, Map<String, Object> bestBefore) {
        if (levels == null) return;
        for (Map.Entry<String, Object> e : levels.entrySet()) {
            Long id = Long.valueOf(e.getKey());
            String level = String.valueOf(e.getValue());
            if (level.isEmpty()) {
                f.pantryService.removeLevel(id, StockLog.ACTION_UNDO, "咕嘟撤销", null);
            } else {
                Object bb = bestBefore == null ? null : bestBefore.get(e.getKey());
                f.stockAppService.adjustLevel(id, level, bb == null ? BestBeforeUpdate.set(null)
                        : BestBeforeUpdate.set(LocalDate.parse(String.valueOf(bb))), "咕嘟撤销");
            }
        }
    }

    // ================================================================= 小工具

    private static PageQuery page(int size) {
        PageQuery q = new PageQuery();
        q.setPageNum(1);
        q.setPageSize(size);
        return q;
    }

    private static BigDecimal decimal(String s) {
        if (s == null) return null;
        try {
            return new BigDecimal(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static List<Long> longs(Object raw) {
        List<Long> out = new ArrayList<>();
        if (raw instanceof Collection<?> c) for (Object o : c) out.add(Long.valueOf(String.valueOf(o)));
        return out;
    }

    private static List<Long> concat(List<Long> a, List<Long> b) {
        Set<Long> s = new LinkedHashSet<>(a);
        s.addAll(b);
        return new ArrayList<>(s);
    }

    @Override
    public java.util.Optional<WeekPlan> draftWeekPlan(LocalDate weekStart, Integer servings, Integer weekdayMaxMinutes) {
        com.gudu.xsd.modules.mealplan.application.dto.MealPlanDraftView v = f.mealPlans.create(memberId,
                new com.gudu.xsd.modules.mealplan.application.dto.CreateDraftCommand(weekStart, null, servings, null,
                        weekdayMaxMinutes, null, true));
        List<PlanDay> days = v.days().stream().map(d -> new PlanDay(d.date(), d.weekday(), d.slots().stream()
                .map(s -> new PlanSlot(s.meal(), s.dish().id(), s.dish().name(), s.dish().coverUrl(), s.badges()))
                .toList())).toList();
        return java.util.Optional.of(new WeekPlan(v.draftId(), v.weekStart(), v.weekEnd(), v.check().passed(),
                v.check().notes(), days));
    }

    @Override
    public Set<String> dishNames() {
        return f.dishService.list(new QueryWrapper<Dish>().select("name")).stream().map(Dish::getName)
                .filter(Objects::nonNull).collect(Collectors.toSet());
    }
}
