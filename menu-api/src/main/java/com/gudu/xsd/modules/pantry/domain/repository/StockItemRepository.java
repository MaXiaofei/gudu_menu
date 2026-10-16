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

    /** 新建或更新（含把保质期置空）；新建回填 id。 */
    void save(StockItem item);

    /** 追加流水（只追加，不改不删）。 */
    void appendLog(StockLogEntry entry);
}
