package com.gudu.xsd.modules.cookbook.domain.repository;

import com.gudu.xsd.modules.cookbook.domain.model.CookedDishRecord;
import com.gudu.xsd.modules.cookbook.domain.model.DishCookStat;
import com.gudu.xsd.modules.cookbook.domain.model.LastCooked;

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

    /** [from, to) 内做了几顿饭：同一次做菜事件（eventId）算一顿，无 eventId 的历史记录每条算一顿。 */
    long countOccasions(java.time.LocalDateTime from, java.time.LocalDateTime to);

    /** 每道菜最近一次做菜记录（不限成员；无记录的菜不含）。 */
    Map<Long, LastCooked> lastCookedOf(Collection<Long> dishIds);

    /** 与该记录同一顿饭的全部记录（同 eventId；无 eventId 时同食集同时间；都没有则只有它自己），按 id 升序。 */
    List<com.gudu.xsd.modules.cookbook.domain.model.CookedRecordRef> sameMealRefs(Long id);

    /** 按 id 取记录的只读引用（不存在返回空）。 */
    java.util.Optional<com.gudu.xsd.modules.cookbook.domain.model.CookedRecordRef> refOf(Long id);
}
