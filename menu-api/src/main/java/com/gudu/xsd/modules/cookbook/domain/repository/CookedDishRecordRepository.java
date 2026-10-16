package com.gudu.xsd.modules.cookbook.domain.repository;

import com.gudu.xsd.modules.cookbook.domain.model.CookedDishRecord;
import com.gudu.xsd.modules.cookbook.domain.model.DishCookStat;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/** 做菜记录仓储（领域端口）。 */
public interface CookedDishRecordRepository {

    void save(CookedDishRecord record);

    boolean existsByEventId(String eventId);

    /** 某次做菜事件写入的记录 id（插入顺序）。 */
    List<Long> idsByEventId(String eventId);

    /** 按菜去重的最近做过（最近优先）。 */
    List<DishCookStat> recentByMember(Long memberId, int limit);

    /** 做过次数最多（次数降序，同次数最近优先）。 */
    List<DishCookStat> mostCookedByMember(Long memberId, int limit);

    /** 批量做过次数（无记录不含）。 */
    Map<Long, Integer> countsByMember(Long memberId, Collection<Long> dishIds);
}
