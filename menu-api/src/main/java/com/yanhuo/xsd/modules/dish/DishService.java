package com.yanhuo.xsd.modules.dish;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yanhuo.xsd.modules.dish.mapper.DishMapper;
import com.yanhuo.xsd.modules.dish.mapper.DishStepMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DishService extends ServiceImpl<DishMapper, Dish> {

    private final DishStepMapper stepMapper;

    /** 保存菜品（新增或更新）并整体替换其步骤。 */
    @Transactional
    public void saveWithSteps(Dish dish, List<DishStep> steps) {
        saveOrUpdate(dish);
        stepMapper.delete(new QueryWrapper<DishStep>().eq("dish_id", dish.getId()));
        for (DishStep s : steps) {
            s.setId(null);
            s.setDishId(dish.getId());
            stepMapper.insert(s);
        }
    }

    /** 详情：菜品 + 步骤列表（按 sort_order, seq 排序）。 */
    public DishDetail detail(Long id) {
        Dish dish = getById(id);
        List<DishStep> steps = stepMapper.selectList(
                new QueryWrapper<DishStep>().eq("dish_id", id).orderByAsc("sort_order", "seq"));
        return new DishDetail(dish, steps);
    }

    public record DishDetail(Dish dish, List<DishStep> steps) {}
}
