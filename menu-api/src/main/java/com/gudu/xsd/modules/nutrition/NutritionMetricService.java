package com.gudu.xsd.modules.nutrition;

import com.gudu.xsd.modules.nutrition.mapper.NutritionMetricMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 营养指标字典 CRUD（Task 5：纯字典；EAV 值在食材库）。
 * 从 NutritionMetricController 下沉（架构守护：Controller 不得直接依赖 Mapper）。
 */
@Service
@RequiredArgsConstructor
public class NutritionMetricService {

    private final NutritionMetricMapper mapper;

    /**
     * 全量返回营养指标字典。
     * 例外于 DESIGN.md §12.1（列表须分页）：营养指标为少量字典数据，用于下拉选择。
     */
    public List<NutritionMetric> list() {
        return mapper.selectList(null);
    }

    public Long add(NutritionMetric m) {
        mapper.insert(m);
        return m.getId();
    }

    public void update(NutritionMetric m) {
        mapper.updateById(m);
    }

    public void delete(Long id) {
        mapper.deleteById(id);
    }
}
