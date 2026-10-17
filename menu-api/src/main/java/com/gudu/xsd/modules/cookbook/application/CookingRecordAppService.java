package com.gudu.xsd.modules.cookbook.application;

import com.gudu.xsd.modules.cookbook.application.dto.RecentDishView;
import com.gudu.xsd.modules.cookbook.domain.model.CookedDishRecord;
import com.gudu.xsd.modules.cookbook.domain.model.DishCookStat;
import com.gudu.xsd.modules.cookbook.domain.model.LastCooked;
import com.gudu.xsd.modules.cookbook.domain.repository.CookedDishRecordRepository;
import com.gudu.xsd.modules.cookbook.domain.service.UsedMemoFormatter;
import com.gudu.xsd.modules.dish.Dish;
import com.gudu.xsd.modules.dish.DishService;
import com.gudu.xsd.modules.menu.domain.event.DishCookedEvent;
import com.gudu.xsd.modules.menu.domain.model.CookedDish;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 食集/做菜记录应用服务（cookbook 上下文 API）：DishCookedEvent 落 cooking_record、最近做过、做过次数。
 */
@Service
@RequiredArgsConstructor
public class CookingRecordAppService {

    public static final int MAX_RECENT = 30;

    private final CookedDishRecordRepository repository;
    private final DishService dishService;
    private final Clock clock;

    /** 消费做菜事件：每道菜一条记录；同一 eventId 已写过则跳过（幂等）。 */
    @Transactional(rollbackFor = Exception.class)
    public List<Long> recordCooked(DishCookedEvent event) {
        if (repository.existsByEventId(event.eventId())) return repository.idsByEventId(event.eventId());
        LocalDateTime cookedAt = LocalDateTime.ofInstant(event.occurredOn(), clock.getZone());
        String memo = UsedMemoFormatter.format(event.usedUp(), event.partiallyUsed());
        List<Long> ids = new ArrayList<>();
        for (CookedDish d : event.dishes()) {
            CookedDishRecord r = CookedDishRecord.cooked(event.eventId(), event.memberId(), d.dishId(),
                    event.menuId(), d.servingFactor(), event.source().code(), memo, event.note(), cookedAt);
            repository.save(r);
            ids.add(r.id());
        }
        return ids;
    }

    /** 某次做菜事件写入的记录 id。 */
    public List<Long> recordIdsOf(String eventId) {
        return repository.idsByEventId(eventId);
    }

    /** 最近做过（当前账号，按菜去重，最近优先；limit 1..30）。已删除的菜不返回。 */
    public List<RecentDishView> recent(Long memberId, int limit) {
        int n = Math.max(1, Math.min(limit, MAX_RECENT));
        List<DishCookStat> stats = repository.recentByMember(memberId, n);
        if (stats.isEmpty()) return List.of();
        Map<Long, Dish> dishById = dishService.listByIds(stats.stream().map(DishCookStat::dishId).toList())
                .stream().collect(Collectors.toMap(Dish::getId, Function.identity(), (a, b) -> a));
        return stats.stream()
                .filter(s -> dishById.containsKey(s.dishId()))
                .map(s -> {
                    Dish d = dishById.get(s.dishId());
                    return new RecentDishView(d.getId(), d.getName(), d.getCoverUrl(), s.lastCookedAt(), s.count());
                })
                .toList();
    }

    /** 你常做：做过次数降序。 */
    public List<DishCookStat> mostCooked(Long memberId, int limit) {
        return repository.mostCookedByMember(memberId, limit);
    }

    /** 批量做过次数（无记录的菜不含）。 */
    public Map<Long, Integer> cookedCounts(Long memberId, Collection<Long> dishIds) {
        return repository.countsByMember(memberId, dishIds);
    }

    /** 本自然月做了几顿饭（「我的」统计；当前单家庭统计全部成员）。 */
    public long occasionsThisMonth() {
        java.time.LocalDate first = java.time.LocalDate.now(clock).withDayOfMonth(1);
        return repository.countOccasions(first.atStartOfDay(), first.plusMonths(1).atStartOfDay());
    }

    /** 每道菜最近一次谁做的、什么时候（菜谱列表「上次 3 天前 · 妈妈做的」）。 */
    public Map<Long, LastCooked> lastCooked(Collection<Long> dishIds) {
        return repository.lastCookedOf(dishIds);
    }
}
