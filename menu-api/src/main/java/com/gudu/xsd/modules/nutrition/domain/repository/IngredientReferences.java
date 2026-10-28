package com.gudu.xsd.modules.nutrition.domain.repository;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 食材被各业务表引用的情况（仓储端口）：删除前的「在用」检查 + 运营收编时的合并改指向。
 * 平台级操作，不按家庭过滤（私有食材只会被本家数据引用，合并时要跨家庭统一处理）。
 */
public interface IngredientReferences {

    /** 该食材被哪些表引用：中文标签（库存/采购/菜谱…）-> 引用行数；没被引用返回空 map。 */
    Map<String, Long> usage(Long ingredientId);

    /**
     * 把 sourceIds 的全部引用改指向 targetId（调用方负责事务）。遇唯一键冲突按固定规则合并，规则见实现类注释。
     */
    MergeStats merge(Collection<Long> sourceIds, Long targetId);

    /** 合并统计：表 -> 改指向行数 / 因冲突合并掉（删除或并入目标行）的行数。 */
    record MergeStats(Map<String, Integer> moved, Map<String, Integer> merged) {

        public static MergeStats empty() {
            return new MergeStats(new LinkedHashMap<>(), new LinkedHashMap<>());
        }

        public int totalMoved() {
            return moved.values().stream().mapToInt(Integer::intValue).sum();
        }

        public int totalMerged() {
            return merged.values().stream().mapToInt(Integer::intValue).sum();
        }
    }
}
