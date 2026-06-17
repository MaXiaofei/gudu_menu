package com.yanhuo.xsd.modules.menu;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 菜单汇总纯函数：总价 = Σ(菜品价 × 份数)；营养 = Σ(各菜份数营养按指标累加)。
 * 算法地基，不依赖外部状态。MenuController.summary 组装 MenuLine 后调用本服务。
 */
@Service
public class MenuCalcService {

    /** 一道菜在菜单中的一行：价格、该菜 1 份的营养(metricId->value)、该菜份数。 */
    public record MenuLine(BigDecimal price, Map<Long, BigDecimal> dishNutrition, BigDecimal servingFactor) {}

    /** 菜单总价。 */
    public BigDecimal totalPrice(List<MenuLine> lines) {
        return lines.stream()
                .map(l -> l.price().multiply(l.servingFactor()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

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
