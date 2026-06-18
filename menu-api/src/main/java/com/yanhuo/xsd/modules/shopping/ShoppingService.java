package com.yanhuo.xsd.modules.shopping;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yanhuo.xsd.common.PageQuery;
import com.yanhuo.xsd.modules.dict.SysDict;
import com.yanhuo.xsd.modules.dict.mapper.DictMapper;
import com.yanhuo.xsd.modules.dish.DishIngredient;
import com.yanhuo.xsd.modules.dish.mapper.DishIngredientMapper;
import com.yanhuo.xsd.modules.mealplan.MealPlan;
import com.yanhuo.xsd.modules.mealplan.MealPlanItem;
import com.yanhuo.xsd.modules.mealplan.mapper.MealPlanItemMapper;
import com.yanhuo.xsd.modules.mealplan.mapper.MealPlanMapper;
import com.yanhuo.xsd.modules.menu.MenuDish;
import com.yanhuo.xsd.modules.menu.mapper.MenuDishMapper;
import com.yanhuo.xsd.modules.nutrition.Ingredient;
import com.yanhuo.xsd.modules.nutrition.mapper.IngredientMapper;
import com.yanhuo.xsd.modules.notification.NotificationPayload;
import com.yanhuo.xsd.modules.notification.NotificationService;
import com.yanhuo.xsd.modules.shopping.ShoppingAggregator.Usage;
import com.yanhuo.xsd.modules.shopping.mapper.ShoppingItemMapper;
import com.yanhuo.xsd.modules.shopping.mapper.ShoppingListMapper;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 采购清单服务（redesign）。
 *
 * <p>核心流程 generate(sourceType, sourceId/sourceIds)：
 * <ul>
 *   <li>menu  → 查 menu_dish 拿 dish_id 列表；</li>
 *   <li>dish  → 直接用传入的 dish_ids；</li>
 *   <li>plan  → 查 meal_plan_item 拿 dish_id 列表。</li>
 * </ul>
 * 然后查各 dish 的 dish_ingredient(ingredient_id + amount × servingFactor)
 * → join ingredient 拿 purchaseCategoryId → 组装 Usage →
 * aggregator.aggregate（按 ingredient_id 去重，合计 referenceGrams）
 * → 落 shopping_list + shopping_item 草稿（purchase_amount/unit 留 null）。
 *
 * <p>用户在前端填 purchase_amount + purchase_unit_id → updatePurchase。
 *
 * <p>不做估价。VO 带中文（食材名/采购单位名 斤把个，枚举铁律）。
 *
 * <p>参照 MealPlanService / PantryService 范式：ServiceImpl 主表 + 显式多 Mapper 构造。
 */
@Service
public class ShoppingService extends ServiceImpl<ShoppingListMapper, ShoppingList> {

    private final ShoppingItemMapper itemMapper;
    private final MealPlanItemMapper mealPlanItemMapper;
    private final MealPlanMapper mealPlanMapper;
    private final DishIngredientMapper dishIngredientMapper;
    private final IngredientMapper ingredientMapper;
    private final DictMapper dictMapper;
    private final MenuDishMapper menuDishMapper;
    private final ShoppingAggregator aggregator;
    private final NotificationService notificationService;

    @Autowired
    public ShoppingService(ShoppingItemMapper itemMapper,
                           MealPlanItemMapper mealPlanItemMapper,
                           MealPlanMapper mealPlanMapper,
                           DishIngredientMapper dishIngredientMapper,
                           IngredientMapper ingredientMapper,
                           DictMapper dictMapper,
                           MenuDishMapper menuDishMapper,
                           ShoppingAggregator aggregator,
                           NotificationService notificationService) {
        this.itemMapper = itemMapper;
        this.mealPlanItemMapper = mealPlanItemMapper;
        this.mealPlanMapper = mealPlanMapper;
        this.dishIngredientMapper = dishIngredientMapper;
        this.ingredientMapper = ingredientMapper;
        this.dictMapper = dictMapper;
        this.menuDishMapper = menuDishMapper;
        this.aggregator = aggregator;
        this.notificationService = notificationService;
    }

    // ===================== 生成（三数据源） =====================

    /**
     * 通用生成：根据数据源类型解析涉及的 dish_id 列表，聚合食材用量，落库采购草稿。
     *
     * @param sourceType 数据源：menu / dish / plan
     * @param sourceId   menu 或 plan 的 id（dish 时可空，用 sourceIds）
     * @param sourceIds  dish 数据源时的多选 dish_id 列表
     * @return 新生成的 shopping_list.id
     */
    @Transactional
    public Long generate(String sourceType, Long sourceId, List<Long> sourceIds) {
        List<DishUsage> dishUsages = resolveDishes(sourceType, sourceId, sourceIds);

        // 收集 dish_id → 各菜用量（dish_ingredient）
        List<Long> dishIds = dishUsages.stream().map(DishUsage::dishId).distinct().collect(Collectors.toList());
        List<DishIngredient> dis = dishIds.isEmpty()
                ? List.of()
                : dishIngredientMapper.selectList(new QueryWrapper<DishIngredient>().in("dish_id", dishIds));

        // dish_id → servingFactor
        Map<Long, BigDecimal> factorByDish = new HashMap<>();
        for (DishUsage du : dishUsages) {
            factorByDish.merge(du.dishId, du.servingFactor, BigDecimal::add);
        }

        // ingredient_id → ingredient（拿 purchase_category）
        List<Long> ingIds = dis.stream().map(DishIngredient::getIngredientId).distinct().collect(Collectors.toList());
        Map<Long, Ingredient> ingById = ingIds.isEmpty()
                ? Map.of()
                : ingredientMapper.selectList(new QueryWrapper<Ingredient>().in("id", ingIds)).stream()
                        .collect(Collectors.toMap(Ingredient::getId, i -> i, (a, b) -> a));

        // 组装 Usage（克数 × 份数系数）
        List<Usage> usages = new ArrayList<>();
        for (DishIngredient di : dis) {
            Ingredient ing = ingById.get(di.getIngredientId());
            if (ing == null) continue;
            BigDecimal factor = factorByDish.getOrDefault(di.getDishId(), BigDecimal.ONE);
            BigDecimal amount = di.getAmount() == null ? BigDecimal.ZERO : di.getAmount();
            usages.add(new Usage(ing.getId(), amount.multiply(factor), ing.getPurchaseCategoryId()));
        }

        // 聚合（按 ingredient_id 去重 → referenceGrams）
        List<ShoppingAggregator.ShoppingLine> lines = aggregator.aggregate(usages);

        // 落库
        ShoppingList list = newList(sourceType, sourceId);
        save(list);
        for (ShoppingAggregator.ShoppingLine l : lines) {
            ShoppingItem item = new ShoppingItem();
            item.setListId(list.getId());
            item.setIngredientId(l.ingredientId());
            item.setReferenceGrams(l.referenceGrams());
            item.setPurchaseCategoryId(l.purchaseCategoryId());
            // 兼容旧字段：referenceGrams 同步写 totalAmount（参考用，不删列）
            item.setTotalAmount(l.referenceGrams());
            item.setPurchased(0);
            // purchase_amount / purchase_unit_id 留 null（用户后填）
            itemMapper.insert(item);
        }

        notifyShoppingGenerated(sourceType, lines.size());
        return list.getId();
    }

    /** 一笔 dish 用量：dish_id + 份数系数（plan 取排菜项份数；menu/dish 默认 1）。 */
    private record DishUsage(Long dishId, BigDecimal servingFactor) {}

    /** 根据数据源类型解析涉及的 dish_id 列表（含份数系数）。 */
    private List<DishUsage> resolveDishes(String sourceType, Long sourceId, List<Long> sourceIds) {
        if (sourceType == null) sourceType = "plan";
        switch (sourceType) {
            case "menu": {
                if (sourceId == null) return List.of();
                List<MenuDish> mds = menuDishMapper.selectList(
                        new QueryWrapper<MenuDish>().eq("menu_id", sourceId));
                List<DishUsage> out = new ArrayList<>();
                for (MenuDish md : mds) {
                    BigDecimal f = md.getServingFactor() == null ? BigDecimal.ONE : md.getServingFactor();
                    out.add(new DishUsage(md.getDishId(), f));
                }
                return out;
            }
            case "dish": {
                List<Long> ids = sourceIds == null ? List.of() : sourceIds;
                List<DishUsage> out = new ArrayList<>();
                for (Long did : ids) {
                    if (did != null) out.add(new DishUsage(did, BigDecimal.ONE));
                }
                return out;
            }
            case "plan":
            default: {
                if (sourceId == null) return List.of();
                List<MealPlanItem> planItems = mealPlanItemMapper.selectList(
                        new QueryWrapper<MealPlanItem>().eq("plan_id", sourceId));
                List<DishUsage> out = new ArrayList<>();
                for (MealPlanItem pi : planItems) {
                    BigDecimal f = pi.getServingFactor() == null ? BigDecimal.ONE : pi.getServingFactor();
                    out.add(new DishUsage(pi.getDishId(), f));
                }
                return out;
            }
        }
    }

    /** 给当前 session 的就餐成员发「采购清单已生成」站内通知；无 session 则跳过。 */
    private void notifyShoppingGenerated(String sourceType, int itemCount) {
        Long memberId;
        try {
            memberId = StpUtil.getSession().getLong("currentMemberId");
        } catch (Exception e) {
            return;
        }
        if (memberId == null) return;
        String src = sourceType == null ? "周计划" :
                ("menu".equals(sourceType) ? "菜单" : "dish".equals(sourceType) ? "菜品" : "周计划");
        notificationService.send(
                new NotificationPayload(memberId, "shopping",
                        "采购清单已生成",
                        src + "已生成采购清单，共 " + itemCount + " 项"),
                "in_app");
    }

    private ShoppingList newList(String sourceType, Long sourceId) {
        ShoppingList list = new ShoppingList();
        list.setSourcePlanId("plan".equals(sourceType) ? sourceId : null);
        list.setTimeRange(sourceType);
        // menu/dish 来源没有固定周区间，用当天；plan 来源取该计划的周区间
        if ("plan".equals(sourceType) && sourceId != null) {
            MealPlan plan = mealPlanMapper.selectById(sourceId);
            if (plan != null && plan.getWeekStart() != null) {
                list.setStartDate(plan.getWeekStart());
                list.setEndDate(plan.getWeekStart().plusDays(6));
                return list;
            }
        }
        list.setStartDate(LocalDate.now());
        list.setEndDate(LocalDate.now());
        return list;
    }

    // ===================== 查询 =====================

    /** 采购清单详情：含 items（带中文）+ 按品类分区视图。 */
    public ShoppingListVO getDetail(Long listId) {
        ShoppingList list = getById(listId);
        List<ShoppingItem> rows = itemMapper.selectList(
                new QueryWrapper<ShoppingItem>().eq("list_id", listId)
                        .orderByAsc("purchase_category_id").orderByAsc("ingredient_id"));
        List<ShoppingItemVO> items = fillVoNames(rows);
        ShoppingListVO vo = new ShoppingListVO();
        BeanUtils.copyProperties(list, vo);
        vo.setItems(items);
        // 分区视图
        Map<Long, List<ShoppingItemVO>> grouped = new LinkedHashMap<>();
        Map<Long, String> catNames = new LinkedHashMap<>();
        for (ShoppingItemVO it : items) {
            grouped.computeIfAbsent(it.getPurchaseCategoryId(), k -> new ArrayList<>()).add(it);
            if (it.getPurchaseCategoryId() != null) {
                catNames.putIfAbsent(it.getPurchaseCategoryId(), it.getPurchaseCategoryName());
            }
        }
        vo.setGrouped(grouped);
        vo.setCategoryNames(catNames);
        return vo;
    }

    /** 分页查采购清单（后台管理，不含 items，按创建时间倒序）。 */
    public IPage<ShoppingList> page(PageQuery q) {
        return page(new Page<>(q.getPageNum(), q.getPageSize()),
                new QueryWrapper<ShoppingList>().orderByDesc("created_at"));
    }

    /** 用户填采购量 + 采购单位（PUT /shopping/item/{id}）。 */
    public void updatePurchase(Long itemId, BigDecimal purchaseAmount, Long purchaseUnitId) {
        ShoppingItem it = itemMapper.selectById(itemId);
        if (it == null) return;
        it.setPurchaseAmount(purchaseAmount);
        it.setPurchaseUnitId(purchaseUnitId);
        itemMapper.updateById(it);
    }

    /** 勾选/取消勾选某明细已买。 */
    public void togglePurchased(Long itemId) {
        ShoppingItem it = itemMapper.selectById(itemId);
        if (it == null) return;
        int cur = it.getPurchased() == null ? 0 : it.getPurchased();
        it.setPurchased(cur == 1 ? 0 : 1);
        itemMapper.updateById(it);
    }

    /** 删除某明细。 */
    public void deleteItem(Long itemId) {
        itemMapper.deleteById(itemId);
    }

    /** 删除整张清单。 */
    public void deleteList(Long listId) {
        itemMapper.delete(new QueryWrapper<ShoppingItem>().eq("list_id", listId));
        removeById(listId);
    }

    // ===================== 内部辅助 =====================

    /** 给 item 列表填中文展示名（食材名/单位名/品类名/采购单位名）。 */
    private List<ShoppingItemVO> fillVoNames(List<ShoppingItem> rows) {
        if (rows.isEmpty()) return new ArrayList<>();
        // 食材名
        List<Long> ingIds = rows.stream().map(ShoppingItem::getIngredientId).distinct().collect(Collectors.toList());
        Map<Long, String> ingName = ingredientMapper.selectList(new QueryWrapper<Ingredient>().in("id", ingIds))
                .stream().collect(Collectors.toMap(Ingredient::getId, Ingredient::getName, (a, b) -> a));
        // 单位 + 品类 + 采购单位字典（一次性查三组）
        List<SysDict> dicts = dictMapper.selectList(
                new QueryWrapper<SysDict>().in("dict_group", List.of("unit", "purchase_category", "purchase_unit")));
        Map<Long, String> unitName = new HashMap<>();
        Map<Long, String> catName = new HashMap<>();
        Map<Long, String> purchaseUnitName = new HashMap<>();
        for (SysDict d : dicts) {
            switch (d.getDictGroup()) {
                case "unit" -> unitName.put(d.getId(), d.getName());
                case "purchase_category" -> catName.put(d.getId(), d.getName());
                case "purchase_unit" -> purchaseUnitName.put(d.getId(), d.getName());
            }
        }
        List<ShoppingItemVO> out = new ArrayList<>(rows.size());
        for (ShoppingItem it : rows) {
            ShoppingItemVO vo = new ShoppingItemVO();
            BeanUtils.copyProperties(it, vo);
            vo.setIngredientName(ingName.get(it.getIngredientId()));
            vo.setUnitName(unitName.get(it.getUnitId()));
            vo.setPurchaseCategoryName(catName.get(it.getPurchaseCategoryId()));
            vo.setPurchaseUnitName(purchaseUnitName.get(it.getPurchaseUnitId()));
            out.add(vo);
        }
        return out;
    }
}
