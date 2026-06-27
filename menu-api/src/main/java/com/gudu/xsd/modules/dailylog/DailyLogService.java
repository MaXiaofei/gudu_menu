package com.gudu.xsd.modules.dailylog;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gudu.xsd.modules.dailylog.mapper.DailyLogItemMapper;
import com.gudu.xsd.modules.dailylog.mapper.DailyLogMapper;
import com.gudu.xsd.modules.dish.DishQueryService;
import com.gudu.xsd.modules.nutrition.IngredientService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 每日饮食记录服务。
 *
 * - submit：session 取 currentMemberId，存 log + 级联 items
 * - listByDate：查当天日志(含 items)
 * - nutritionSummary：拉 items → 组装 Intake（dish 项复用 DishQueryService.nutrition per份；
 *   ingredient 项用 IngredientService.nutritionOf per100g）→ DailyLogCalcService.aggregateIntake 聚合
 *
 * 营养汇总不重造算法：dish per份营养已由 DishQueryService 通过 NutritionCalcService 聚合，
 * 此处只需按份数缩放；ingredient 按 per100g 缩放克数。两者统一进 aggregateIntake 累加。
 */
@Service
@RequiredArgsConstructor
public class DailyLogService {

    private final DailyLogMapper logMapper;
    private final DailyLogItemMapper itemMapper;
    private final DishQueryService dishQueryService;
    private final IngredientService ingredientService;
    private final DailyLogCalcService calc;

    /** 当前就餐成员 id（session，参照 ReviewService/NotificationController）。 */
    public Long currentMemberId() {
        return StpUtil.getSession().getLong("currentMemberId");
    }

    /** 提交一天日志：存 log + 级联 items。返回 logId。 */
    @Transactional
    public Long submit(DailyLogSaveDTO dto) {
        DailyLog log = new DailyLog();
        log.setMemberId(currentMemberId());
        log.setDate(dto.getDate());
        log.setNote(dto.getNote());
        logMapper.insert(log);

        if (dto.getItems() != null) {
            for (DailyLogSaveDTO.Item it : dto.getItems()) {
                DailyLogItem row = new DailyLogItem();
                row.setLogId(log.getId());
                row.setDishId(it.getDishId());
                row.setIngredientId(it.getIngredientId());
                row.setAmount(it.getAmount());
                row.setServingFactor(it.getServingFactor());
                itemMapper.insert(row);
            }
        }
        return log.getId();
    }

    /** 查当天日志（含 items）。无则返回 null。 */
    public DailyLogVO listByDate(Long memberId, LocalDate date) {
        DailyLog log = logMapper.selectOne(new QueryWrapper<DailyLog>()
                .eq("member_id", memberId)
                .eq("`date`", date)
                .last("LIMIT 1"));
        if (log == null) return null;
        DailyLogVO vo = new DailyLogVO();
        vo.setId(log.getId());
        vo.setMemberId(log.getMemberId());
        vo.setDate(log.getDate());
        vo.setNote(log.getNote());
        vo.setCreateTime(log.getCreateTime());
        vo.setItems(listItems(log.getId()));
        return vo;
    }

    private List<DailyLogItem> listItems(Long logId) {
        return itemMapper.selectList(new QueryWrapper<DailyLogItem>()
                .eq("log_id", logId).orderByAsc("id"));
    }

    /**
     * 汇总某日志的总营养（metricId → value）。
     * dish 项：DishQueryService.nutrition(dishId, servingFactor) 得 per份营养，
     *         qty = amount(份数)；
     * ingredient 项：IngredientService.nutritionOf(ingredientId) 得 per100g，
     *         qty = amount(克)。
     * 全部交给 {@link DailyLogCalcService#aggregateIntake} 累加。
     */
    public Map<Long, BigDecimal> nutritionSummary(Long logId) {
        List<DailyLogItem> items = listItems(logId);
        List<DailyLogCalcService.Intake> intakes = new ArrayList<>();
        for (DailyLogItem it : items) {
            BigDecimal qty = it.getAmount() == null ? BigDecimal.ZERO : it.getAmount();
            BigDecimal serving = it.getServingFactor() == null ? BigDecimal.ONE : it.getServingFactor();
            if (it.getDishId() != null) {
                // 该菜 per份营养(已按 servingFactor 缩放)，qty=份数
                Map<Long, BigDecimal> perServing = dishQueryService.nutrition(it.getDishId(), serving);
                for (Map.Entry<Long, BigDecimal> e : perServing.entrySet()) {
                    intakes.add(new DailyLogCalcService.Intake(e.getKey(), true, e.getValue(), qty));
                }
            } else if (it.getIngredientId() != null) {
                // 该食材 per100g，qty=克
                Map<Long, BigDecimal> per100g = ingredientService.nutritionOf(it.getIngredientId());
                for (Map.Entry<Long, BigDecimal> e : per100g.entrySet()) {
                    intakes.add(new DailyLogCalcService.Intake(e.getKey(), false, e.getValue(), qty));
                }
            }
        }
        return calc.aggregateIntake(intakes);
    }
}
