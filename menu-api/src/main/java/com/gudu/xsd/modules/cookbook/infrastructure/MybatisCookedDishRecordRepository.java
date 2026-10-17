package com.gudu.xsd.modules.cookbook.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gudu.xsd.modules.cookbook.CookingRecord;
import com.gudu.xsd.modules.cookbook.domain.model.CookedDishRecord;
import com.gudu.xsd.modules.cookbook.domain.model.DishCookStat;
import com.gudu.xsd.modules.cookbook.domain.model.LastCooked;
import com.gudu.xsd.modules.cookbook.domain.repository.CookedDishRecordRepository;
import com.gudu.xsd.modules.cookbook.mapper.CookingRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** {@link CookedDishRecordRepository} 的 MyBatis-Plus 实现（cooking_record，PO = CookingRecord）。 */
@Repository
@RequiredArgsConstructor
public class MybatisCookedDishRecordRepository implements CookedDishRecordRepository {

    private final CookingRecordMapper mapper;

    @Override
    public void save(CookedDishRecord r) {
        CookingRecord po = new CookingRecord();
        po.setEventId(r.eventId());
        po.setMemberId(r.memberId());
        po.setDishId(r.dishId());
        po.setMenuId(r.menuId());
        po.setServingFactor(r.servingFactor());
        po.setSource(r.source());
        po.setMemo(r.memo());
        po.setNote(r.note());
        po.setCookedAt(r.cookedAt());
        mapper.insert(po);
        r.identify(po.getId());
    }

    @Override
    public boolean existsByEventId(String eventId) {
        if (eventId == null) return false;
        Long n = mapper.selectCount(new QueryWrapper<CookingRecord>().eq("event_id", eventId));
        return n != null && n > 0;
    }

    @Override
    public List<Long> idsByEventId(String eventId) {
        if (eventId == null) return List.of();
        return mapper.selectList(new QueryWrapper<CookingRecord>().select("id")
                        .eq("event_id", eventId).orderByAsc("id"))
                .stream().map(CookingRecord::getId).toList();
    }

    @Override
    public List<DishCookStat> recentByMember(Long memberId, int limit) {
        if (memberId == null || limit <= 0) return List.of();
        return toStats(mapper.selectMaps(statQuery(memberId)
                .orderByDesc("last_at").last("LIMIT " + limit)));
    }

    @Override
    public List<DishCookStat> mostCookedByMember(Long memberId, int limit) {
        if (memberId == null || limit <= 0) return List.of();
        return toStats(mapper.selectMaps(statQuery(memberId)
                .orderByDesc("cnt").orderByDesc("last_at").last("LIMIT " + limit)));
    }

    @Override
    public Map<Long, Integer> countsByMember(Long memberId, Collection<Long> dishIds) {
        List<Long> ids = dishIds == null ? List.of() : dishIds.stream().filter(Objects::nonNull).distinct().toList();
        if (memberId == null || ids.isEmpty()) return Map.of();
        Map<Long, Integer> out = new HashMap<>();
        for (DishCookStat s : toStats(mapper.selectMaps(statQuery(memberId).in("dish_id", ids)))) {
            out.put(s.dishId(), s.count());
        }
        return out;
    }

    @Override
    public long countOccasions(LocalDateTime from, LocalDateTime to) {
        List<Map<String, Object>> rows = mapper.selectMaps(new QueryWrapper<CookingRecord>()
                .select("COUNT(DISTINCT COALESCE(event_id, CONCAT('r', id))) AS cnt")
                .ge("cooked_at", from)
                .lt("cooked_at", to));
        if (rows.isEmpty() || rows.get(0) == null || rows.get(0).get("cnt") == null) return 0;
        return ((Number) rows.get(0).get("cnt")).longValue();
    }

    @Override
    public Map<Long, LastCooked> lastCookedOf(Collection<Long> dishIds) {
        List<Long> ids = dishIds == null ? List.of() : dishIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return Map.of();
        String in = ids.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        List<CookingRecord> rows = mapper.selectList(new QueryWrapper<CookingRecord>()
                .select("id", "dish_id", "member_id", "cooked_at")
                .inSql("(dish_id, cooked_at)",
                        "SELECT dish_id, MAX(cooked_at) FROM cooking_record WHERE dish_id IN (" + in + ") GROUP BY dish_id")
                .orderByDesc("id"));
        Map<Long, LastCooked> out = new HashMap<>();
        for (CookingRecord r : rows) {
            // 同一时刻多条（整集做同一事件）取 id 最大的一条
            out.putIfAbsent(r.getDishId(), new LastCooked(r.getDishId(), r.getMemberId(), r.getCookedAt()));
        }
        return out;
    }

    private static QueryWrapper<CookingRecord> statQuery(Long memberId) {
        return new QueryWrapper<CookingRecord>()
                .select("dish_id", "MAX(cooked_at) AS last_at", "COUNT(*) AS cnt")
                .eq("member_id", memberId)
                .isNotNull("dish_id")
                .groupBy("dish_id");
    }

    private static List<DishCookStat> toStats(List<Map<String, Object>> rows) {
        return rows.stream()
                .filter(r -> r.get("dish_id") != null)
                .map(r -> new DishCookStat(((Number) r.get("dish_id")).longValue(),
                        toLocalDateTime(r.get("last_at")),
                        r.get("cnt") == null ? 0 : ((Number) r.get("cnt")).intValue()))
                .toList();
    }

    private static LocalDateTime toLocalDateTime(Object v) {
        if (v instanceof LocalDateTime ldt) return ldt;
        if (v instanceof Timestamp ts) return ts.toLocalDateTime();
        return null;
    }

    @Override
    public java.util.Optional<com.gudu.xsd.modules.cookbook.domain.model.CookedRecordRef> refOf(Long id) {
        if (id == null) return java.util.Optional.empty();
        return java.util.Optional.ofNullable(mapper.selectById(id)).map(r ->
                new com.gudu.xsd.modules.cookbook.domain.model.CookedRecordRef(r.getId(), r.getMemberId(),
                        r.getDishId(), r.getCookedAt()));
    }

    @Override
    public List<com.gudu.xsd.modules.cookbook.domain.model.CookedRecordRef> sameMealRefs(Long id) {
        CookingRecord r = id == null ? null : mapper.selectById(id);
        if (r == null) return List.of();
        QueryWrapper<CookingRecord> w = new QueryWrapper<>();
        if (r.getEventId() != null) {
            w.eq("event_id", r.getEventId());
        } else if (r.getMenuId() != null) {
            w.eq("menu_id", r.getMenuId()).eq("cooked_at", r.getCookedAt());
        } else {
            w.eq("id", r.getId());
        }
        return mapper.selectList(w.orderByAsc("id")).stream()
                .map(x -> new com.gudu.xsd.modules.cookbook.domain.model.CookedRecordRef(x.getId(), x.getMemberId(),
                        x.getDishId(), x.getCookedAt())).toList();
    }
}
