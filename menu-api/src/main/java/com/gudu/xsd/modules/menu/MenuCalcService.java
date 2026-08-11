package com.gudu.xsd.modules.menu;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 菜单汇总纯函数：营养 = Σ(各菜份数营养按指标累加)。算法地基，不依赖外部状态。
 * V55：价格链路整体删除（食材去单位后不再按克计价），MenuLine 仅保留营养。
 * MenuController.summary 组装 MenuLine 后调用本服务。
 */
@Service
public class MenuCalcService {

    /** 一道菜在菜单中的一行：该菜 1 份的营养(metricId->value)、该菜份数。 */
    public record MenuLine(Map<Long, BigDecimal> dishNutrition, BigDecimal servingFactor) {}

    /** 菜单营养汇总：各菜营养按份数缩放后，按指标累加。 */
    public Map<Long, BigDecimal> totalNutrition(List<MenuLine> lines) {
        Map<Long, BigDecimal> sum = new HashMap<>();
        for (MenuLine l : lines) {
            for (var e : l.dishNutrition().entrySet()) {
                sum.merge(e.getKey(), e.getValue().multiply(l.servingFactor()), BigDecimal::add);
            }
        }
        return sum;
    }
}
