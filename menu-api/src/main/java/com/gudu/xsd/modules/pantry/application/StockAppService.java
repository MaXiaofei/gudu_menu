package com.gudu.xsd.modules.pantry.application;

import com.gudu.xsd.common.BizException;
import com.gudu.xsd.common.domain.DomainEventPublisher;
import com.gudu.xsd.modules.dict.DictService;
import com.gudu.xsd.modules.dict.SysDict;
import com.gudu.xsd.modules.nutrition.Ingredient;
import com.gudu.xsd.modules.nutrition.IngredientService;
import com.gudu.xsd.modules.pantry.application.dto.BestBeforeUpdate;
import com.gudu.xsd.modules.pantry.application.dto.ExpiringItemView;
import com.gudu.xsd.modules.pantry.application.dto.StockShelfView;
import com.gudu.xsd.modules.pantry.domain.model.StockAction;
import com.gudu.xsd.modules.pantry.domain.model.StockChange;
import com.gudu.xsd.modules.pantry.domain.model.StockItem;
import com.gudu.xsd.modules.pantry.domain.model.StockLevel;
import com.gudu.xsd.modules.pantry.domain.model.StockLogEntry;
import com.gudu.xsd.modules.pantry.domain.repository.StockItemRepository;
import com.gudu.xsd.modules.pantry.domain.service.ShelfLifePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 余量应用服务（pantry 上下文 API）：取聚合 → 调行为 → 存 → 记流水 → 发事件。
 * 保质期、做菜回写档位、临期查询、保质期建议都走这里（三档库存为唯一库存模型）。
 */
@Service
@RequiredArgsConstructor
public class StockAppService {

    static final int MAX_EXPIRING_DAYS = 365;

    private final StockItemRepository repository;
    private final DomainEventPublisher eventPublisher;
    private final IngredientService ingredientService;
    private final DictService dictService;
    private final Clock clock;
    private final ShelfLifePolicy shelfLifePolicy = new ShelfLifePolicy();

    /** 手动设档位（PUT /pantry/{id}/level）：没建档则新建；bestBefore 按指令改/清/不动。 */
    @Transactional(rollbackFor = Exception.class)
    public void adjustLevel(Long ingredientId, String level, BestBeforeUpdate bestBefore, String note) {
        if (ingredientId == null) throw new BizException("食材 id 不能为空");
        StockLevel target = StockLevel.of(level);
        BestBeforeUpdate bb = bestBefore == null ? BestBeforeUpdate.keep() : bestBefore;
        Optional<StockItem> existing = repository.findByIngredientId(ingredientId);
        StockItem item;
        StockChange change;
        if (existing.isPresent()) {
            item = existing.get();
            change = item.adjustLevel(target);
            if (bb.present()) item.changeBestBefore(bb.value());
        } else {
            item = StockItem.create(ingredientId, target, bb.present() ? bb.value() : null);
            change = new StockChange(null, target);
        }
        repository.save(item);
        repository.appendLog(StockLogEntry.of(ingredientId, StockAction.MANUAL, change, note));
        eventPublisher.publishFrom(item);
    }

    /** 只改保质期（手动入库后补登 bestBefore）；没建档忽略。 */
    @Transactional(rollbackFor = Exception.class)
    public void changeBestBefore(Long ingredientId, LocalDate bestBefore) {
        repository.findByIngredientId(ingredientId).ifPresent(item -> {
            item.changeBestBefore(bestBefore);
            repository.save(item);
        });
    }

    /**
     * 做菜确认回写（MealCookedEvent 消费）：usedUp → 没有；partiallyUsed → 降一档。
     * 同一食材两边都勾以「用完」为准；「用了一些」的没建档食材不动。一次批量加载，无 N+1。
     */
    @Transactional(rollbackFor = Exception.class)
    public void consume(Collection<Long> usedUp, Collection<Long> partiallyUsed) {
        Set<Long> up = clean(usedUp);
        Set<Long> partial = clean(partiallyUsed);
        partial.removeAll(up);
        if (up.isEmpty() && partial.isEmpty()) return;
        Set<Long> all = new LinkedHashSet<>(up);
        all.addAll(partial);
        Map<Long, StockItem> byIng = repository.findByIngredientIds(all).stream()
                .collect(Collectors.toMap(StockItem::ingredientId, Function.identity(), (a, b) -> a));
        LocalDateTime now = LocalDateTime.now(clock);
        for (Long id : up) {
            StockItem item = byIng.get(id);
            StockChange change;
            if (item == null) {
                item = StockItem.create(id, StockLevel.NONE, null);
                change = new StockChange(null, StockLevel.NONE);
            } else {
                change = item.markUsedUp();
            }
            repository.save(item);
            repository.appendLog(StockLogEntry.of(id, StockAction.COOK, change, null));
            eventPublisher.publishFrom(item);
        }
        for (Long id : partial) {
            StockItem item = byIng.get(id);
            if (item == null) continue;
            StockChange change = item.markPartiallyUsed(now);
            repository.save(item);
            if (change.changed()) {
                repository.appendLog(StockLogEntry.of(id, StockAction.COOK_PARTIAL, change, null));
            }
            eventPublisher.publishFrom(item);
        }
    }

    /** 临期（含已过期）：days 天内到期，按剩余天数升序、同天按名。 */
    public List<ExpiringItemView> expiring(int days) {
        int window = Math.max(0, Math.min(days, MAX_EXPIRING_DAYS));
        LocalDate today = LocalDate.now(clock);
        List<StockItem> hits = repository.findAllWithBestBefore().stream()
                .filter(s -> s.isExpiringWithin(window, today)).toList();
        if (hits.isEmpty()) return List.of();
        Map<Long, String> names = ingredientNames(hits.stream().map(StockItem::ingredientId).toList());
        return hits.stream()
                .filter(s -> names.containsKey(s.ingredientId())) // 食材被删则跳过
                .map(s -> new ExpiringItemView(s.ingredientId(), names.get(s.ingredientId()), s.level().name(),
                        s.bestBefore(), s.daysLeft(today)))
                .sorted(Comparator.comparing(ExpiringItemView::daysLeft)
                        .thenComparing(ExpiringItemView::ingredientName, java.text.Collator.getInstance()))
                .toList();
    }

    /** 批量取档位（未建档不含）。 */
    public Map<Long, StockLevel> levelsOf(Collection<Long> ingredientIds) {
        return repository.findByIngredientIds(clean(ingredientIds)).stream()
                .collect(Collectors.toMap(StockItem::ingredientId, StockItem::level, (a, b) -> a));
    }

    /** 批量取保质期视图（grouped/item 回填；未建档不含）。 */
    public Map<Long, StockShelfView> shelfViewsOf(Collection<Long> ingredientIds) {
        LocalDate today = LocalDate.now(clock);
        Map<Long, StockShelfView> out = new HashMap<>();
        for (StockItem s : repository.findByIngredientIds(clean(ingredientIds))) {
            out.put(s.ingredientId(), new StockShelfView(s.bestBefore(), s.daysLeft(today)));
        }
        return out;
    }

    /** 默认保质天数建议：食材采购品类 → 字典 shelf_life_days → 品类名兜底；都没有返回 null。 */
    public Integer suggestShelfLifeDays(Long ingredientId) {
        if (ingredientId == null) throw new BizException("食材 id 不能为空");
        Ingredient ing = ingredientService.getById(ingredientId);
        if (ing == null) throw new BizException("食材不存在");
        if (ing.getPurchaseCategoryId() == null) return null;
        SysDict category = dictService.getById(ing.getPurchaseCategoryId());
        if (category == null) return null;
        return shelfLifePolicy.suggestedDays(category.getShelfLifeDays(), category.getName()).orElse(null);
    }

    private Map<Long, String> ingredientNames(Collection<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return ingredientService.listByIds(ids).stream()
                .collect(Collectors.toMap(Ingredient::getId, Ingredient::getName, (a, b) -> a));
    }

    private static Set<Long> clean(Collection<Long> ids) {
        Set<Long> out = new LinkedHashSet<>();
        if (ids != null) ids.stream().filter(Objects::nonNull).forEach(out::add);
        return out;
    }
}
