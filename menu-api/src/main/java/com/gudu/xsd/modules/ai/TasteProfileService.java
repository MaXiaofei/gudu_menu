package com.gudu.xsd.modules.ai;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gudu.xsd.modules.cookbook.CookingRecord;
import com.gudu.xsd.modules.cookbook.mapper.CookingRecordMapper;
import com.gudu.xsd.modules.dish.Dish;
import com.gudu.xsd.modules.dish.mapper.DishMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 口味画像（2026-08 向量化推荐）：近期做菜历史 → 口味画像查询文本。
 *
 * <p>实现：cooking_record 近 90 天（近 30 天权重 ×3）按菜聚合权重 → Top 8 菜名，
 * 重复出现的菜名在拼接查询里重复出现（embedding 语义自然向高频口味倾斜），
 * 拼成「口味类似：番茄炒蛋、番茄炒蛋、清蒸鱼」供向量召回复用。
 *
 * <p>查询很轻（单表 SQL），不做缓存；如后续改向量均值方案（pgvector 原生 SQL）再引入 Redis。
 */
@Service
@RequiredArgsConstructor
public class TasteProfileService {

    private final CookingRecordMapper cookingRecordMapper;
    private final DishMapper dishMapper;

    /**
     * 口味画像文本：近期常做菜名按权重重复拼接（加权 Top 8，权重≈重复次数）。
     * 无历史返回空串（调用方跳过画像段）。
     */
    public String profileText(Long memberId) {
        LocalDateTime since = LocalDateTime.now().minusDays(90);
        List<CookingRecord> recs = cookingRecordMapper.selectList(
                new QueryWrapper<CookingRecord>()
                        .eq(memberId != null, "member_id", memberId)
                        .ge("cooked_at", since));
        if (recs.isEmpty()) return "";

        // 菜 → 权重（近 30 天 ×3，31-90 天 ×1）
        LocalDateTime recent = LocalDateTime.now().minusDays(30);
        Map<Long, Integer> weight = new LinkedHashMap<>();
        for (CookingRecord r : recs) {
            if (r.getDishId() == null) continue;
            int w = r.getCookedAt() != null && r.getCookedAt().isAfter(recent) ? 3 : 1;
            weight.merge(r.getDishId(), w, Integer::sum);
        }
        if (weight.isEmpty()) return "";

        List<Long> topIds = weight.entrySet().stream()
                .sorted(Map.Entry.<Long, Integer>comparingByValue().reversed())
                .limit(8).map(Map.Entry::getKey).toList();
        Map<Long, String> names = dishMapper.selectBatchIds(topIds).stream()
                .collect(Collectors.toMap(Dish::getId, Dish::getName, (a, b) -> a));

        // 菜名按权重重复（权重 6 → 出现约 3 次，capped）参与 embedding
        StringBuilder sb = new StringBuilder();
        for (Long id : topIds) {
            String n = names.get(id);
            if (n == null) continue;
            int repeat = Math.max(1, Math.min(3, weight.get(id) / 2));
            sb.append((n + "、").repeat(repeat));
        }
        return sb.length() == 0 ? "" : sb.substring(0, sb.length() - 1);
    }

    /** 近 N 天做过的菜 id（推荐召回时排除刚做过的，避免重复推荐）。 */
    public List<Long> recentDishIds(Long memberId, int days) {
        return cookingRecordMapper.selectList(new QueryWrapper<CookingRecord>()
                        .eq(memberId != null, "member_id", memberId)
                        .ge("cooked_at", LocalDateTime.now().minusDays(days))
                        .isNotNull("dish_id")).stream()
                .map(CookingRecord::getDishId).distinct().toList();
    }
}
