package com.gudu.xsd.modules.pantry.domain.repository;

import com.gudu.xsd.modules.pantry.domain.model.StockItem;
import com.gudu.xsd.modules.pantry.domain.model.StockLogEntry;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 余量聚合仓储（领域端口，实现在 infrastructure）。 */
public interface StockItemRepository {

    Optional<StockItem> findByIngredientId(Long ingredientId);

    /** 批量取（一次查询，避免 N+1）。 */
    List<StockItem> findByIngredientIds(Collection<Long> ingredientIds);

    /** 记了保质期的全部余量（临期查询用，本家通常 < 200 条）。 */
    List<StockItem> findAllWithBestBefore();

    /** 本家全部余量（含「用完」；余量统计用，本家通常 < 200 条）。 */
    List<StockItem> findAll();

    /**
     * 「少扔」计数：[from, to) 内做菜用掉（用完/用了一些）且使用日在保质期前 windowDays 天内（含到期当天）的流水条数。
     */
    long countCookedBeforeExpiry(java.time.LocalDateTime from, java.time.LocalDateTime to, int windowDays);

    /** 新建或更新（含把保质期置空）；新建回填 id。 */
    void save(StockItem item);

    /** 追加流水（只追加，不改不删）。 */
    void appendLog(StockLogEntry entry);
}
