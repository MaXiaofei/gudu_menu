package com.gudu.xsd.modules.pantry.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.gudu.xsd.modules.pantry.IngredientStock;
import com.gudu.xsd.modules.pantry.StockLog;
import com.gudu.xsd.modules.pantry.domain.model.StockAction;
import com.gudu.xsd.modules.pantry.domain.model.StockItem;
import com.gudu.xsd.modules.pantry.domain.model.StockLevel;
import com.gudu.xsd.modules.pantry.domain.model.StockLogEntry;
import com.gudu.xsd.modules.pantry.domain.repository.StockItemRepository;
import com.gudu.xsd.modules.pantry.mapper.IngredientStockMapper;
import com.gudu.xsd.modules.pantry.mapper.StockLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** {@link StockItemRepository} 的 MyBatis-Plus 实现：ingredient_stock（PO = IngredientStock）+ stock_log。 */
@Repository
@RequiredArgsConstructor
public class MybatisStockItemRepository implements StockItemRepository {

    private final IngredientStockMapper stockMapper;
    private final StockLogMapper stockLogMapper;

    @Override
    public Optional<StockItem> findByIngredientId(Long ingredientId) {
        if (ingredientId == null) return Optional.empty();
        IngredientStock po = stockMapper.selectOne(
                new QueryWrapper<IngredientStock>().eq("ingredient_id", ingredientId).last("LIMIT 1"));
        return Optional.ofNullable(po).map(MybatisStockItemRepository::toDomain);
    }

    @Override
    public List<StockItem> findByIngredientIds(Collection<Long> ingredientIds) {
        List<Long> ids = ingredientIds == null ? List.of()
                : ingredientIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return List.of();
        return stockMapper.selectList(new QueryWrapper<IngredientStock>().in("ingredient_id", ids))
                .stream().map(MybatisStockItemRepository::toDomain).toList();
    }

    @Override
    public List<StockItem> findAllWithBestBefore() {
        return stockMapper.selectList(new QueryWrapper<IngredientStock>().isNotNull("best_before"))
                .stream().map(MybatisStockItemRepository::toDomain).toList();
    }

    @Override
    public List<StockItem> findAll() {
        return stockMapper.selectList(null).stream().map(MybatisStockItemRepository::toDomain).toList();
    }

    @Override
    public long countCookedBeforeExpiry(java.time.LocalDateTime from, java.time.LocalDateTime to, int windowDays) {
        Long n = stockLogMapper.selectCount(new QueryWrapper<StockLog>()
                .in("action", StockAction.COOK.code(), StockAction.COOK_PARTIAL.code())
                .isNotNull("best_before")
                .ge("create_time", from)
                .lt("create_time", to)
                .apply("DATE(create_time) >= DATE_SUB(best_before, INTERVAL {0} DAY)", Math.max(0, windowDays))
                .apply("DATE(create_time) <= best_before"));
        return n == null ? 0 : n;
    }

    @Override
    public void save(StockItem item) {
        if (item.id() == null) {
            IngredientStock po = new IngredientStock();
            po.setIngredientId(item.ingredientId());
            po.setLevel(item.level().name());
            po.setBestBefore(item.bestBefore());
            po.setOpenedAt(item.openedAt());
            stockMapper.insert(po);
            item.identify(po.getId());
            return;
        }
        // UpdateWrapper 显式 set：保质期/开封时间允许被清空为 NULL（updateById 会跳过 null 字段）
        stockMapper.update(null, new UpdateWrapper<IngredientStock>()
                .eq("id", item.id())
                .set("level", item.level().name())
                .set("best_before", item.bestBefore())
                .set("opened_at", item.openedAt()));
    }

    @Override
    public void appendLog(StockLogEntry entry) {
        StockLog log = new StockLog();
        log.setIngredientId(entry.ingredientId());
        log.setAction(entry.action().code());
        log.setBeforeLevel(entry.before() == null ? null : entry.before().name());
        log.setAfterLevel(entry.after() == null ? null : entry.after().name());
        log.setNote(entry.note());
        log.setRefId(entry.refId());
        log.setBestBefore(entry.bestBefore());
        stockLogMapper.insert(log);
    }

    static StockItem toDomain(IngredientStock po) {
        return StockItem.rehydrate(po.getId(), po.getIngredientId(), StockLevel.parseOrNone(po.getLevel()),
                po.getBestBefore(), po.getOpenedAt());
    }
}
