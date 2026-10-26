package com.gudu.xsd.modules.dish;

import com.gudu.xsd.common.BizException;
import com.gudu.xsd.modules.dish.mapper.DishMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * 菜谱可见性守卫（multi-tenant-family E.1，dish 上下文对外 API）：凡接收 dishId 的入口（收藏、点评、食集加菜、
 * 采购生成、做菜、餐程换菜、聚餐加菜、社区关联…）在使用 dishId 前调用。
 *
 * <p>读取经 DishVisibilityInnerInterceptor 改写（公共 OR 本家），别家私有菜谱读不到 → 404，不泄露存在性。
 * 公共菜谱可读可引用；改 / 删菜谱本身另由 {@link DishAccessGuard} 校验（公共仅平台运营）。
 */
@Component
@RequiredArgsConstructor
public class DishVisibilityGuard {

    public static final String NOT_FOUND = "菜谱不存在";

    private final DishMapper dishMapper;

    /** 可见则返回菜谱，否则 404。 */
    public Dish requireVisible(Long dishId) {
        Dish d = dishId == null ? null : dishMapper.selectById(dishId);
        if (d == null) throw BizException.notFound(NOT_FOUND);
        return d;
    }

    /** 全部可见才放行（null 忽略），任一不可见 404。 */
    public void requireAllVisible(Collection<Long> dishIds) {
        if (dishIds == null) return;
        List<Long> ids = dishIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return;
        if (dishMapper.selectBatchIds(ids).size() != ids.size()) throw BizException.notFound(NOT_FOUND);
    }
}
