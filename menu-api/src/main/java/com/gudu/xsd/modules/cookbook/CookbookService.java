package com.gudu.xsd.modules.cookbook;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gudu.xsd.modules.cookbook.mapper.CookingRecordMapper;
import com.gudu.xsd.modules.cookbook.mapper.FavoriteMapper;
import com.gudu.xsd.modules.dish.Dish;
import com.gudu.xsd.modules.dish.DishIngredient;
import com.gudu.xsd.modules.dish.mapper.DishIngredientMapper;
import com.gudu.xsd.modules.dish.mapper.DishMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CookbookService {

    /** 部分匹配：最多允许缺这么多食材（再多视为「差太多」，不推荐）。 */
    static final int MAX_MISSING = 2;

    private final FavoriteMapper favoriteMapper;
    private final CookingRecordMapper cookingRecordMapper;
    private final DishMapper dishMapper;
    private final DishIngredientMapper dishIngredientMapper;

    /** 收藏（幂等：已收藏则跳过）。 */
    public void favorite(Long memberId, Long dishId) {
        Long cnt = favoriteMapper.selectCount(new QueryWrapper<Favorite>()
                .eq("member_id", memberId).eq("dish_id", dishId));
        if (cnt == 0) {
            Favorite f = new Favorite();
            f.setMemberId(memberId);
            f.setDishId(dishId);
            favoriteMapper.insert(f);
        }
    }

    public void unfavorite(Long memberId, Long dishId) {
        favoriteMapper.delete(new QueryWrapper<Favorite>()
                .eq("member_id", memberId).eq("dish_id", dishId));
    }

    /** 某成员收藏的菜品列表。 */
    public List<Dish> listFavorites(Long memberId) {
        List<Favorite> favs = favoriteMapper.selectList(
                new QueryWrapper<Favorite>().eq("member_id", memberId));
        if (favs.isEmpty()) return List.of();
        List<Long> ids = favs.stream().map(Favorite::getDishId).toList();
        return dishMapper.selectBatchIds(ids);
    }

    /** 标记做过：写一条 cooking_record。 */
    public void markDone(Long memberId, Long dishId, String note) {
        CookingRecord r = new CookingRecord();
        r.setDishId(dishId);
        r.setMemberId(memberId);
        r.setCookedAt(LocalDateTime.now());
        r.setNote(note);
        cookingRecordMapper.insert(r);
    }

    /** 做过的菜品（按 cooking_record 去重 dish_id，最近优先）。 */
    public List<Dish> listDone(Long memberId) {
        List<CookingRecord> records = cookingRecordMapper.selectList(
                new QueryWrapper<CookingRecord>().eq("member_id", memberId).orderByDesc("cooked_at"));
        if (records.isEmpty()) return List.of();
        List<Long> ids = records.stream().map(CookingRecord::getDishId).distinct().toList();
        return dishMapper.selectBatchIds(ids);
    }

    // ==================== 反向找菜（勾选食材 → 推荐能做的菜） ====================

    /**
     * 反向找菜入口：给定「我有的食材 id」，找出这些食材能做出的菜。
     * 全匹配（菜的食材 ⊆ 所选）排前并标记可做；部分匹配（缺 ≤ {@value #MAX_MISSING}）排后并标注缺失；
     * 缺得更多或无交集的菜不返回（避免噪声）。
     */
    public List<DishMatch> findDishesByIngredients(List<Long> ingredientIds) {
        if (ingredientIds == null || ingredientIds.isEmpty()) {
            return List.of();
        }
        List<DishIngredient> all = dishIngredientMapper.selectList(null);
        List<Long> dishIds = all.stream().map(DishIngredient::getDishId).distinct().toList();
        if (dishIds.isEmpty()) {
            return List.of();
        }
        Map<Long, Dish> dishById = dishMapper.selectBatchIds(dishIds).stream()
                .collect(Collectors.toMap(Dish::getId, d -> d));
        return rankDishes(all, dishById, Map.of(), new HashSet<>(ingredientIds));
    }

    /**
     * 纯函数：按食材匹配度给菜排序。不碰任何 Mapper，便于单测。
     *
     * @param all         全量 dish_ingredient 关系
     * @param dishById    dishId → Dish（决定哪些菜入榜；缺菜的视为已删，跳过）
     * @param ingredientName ingredientId → 名称（缺失食材标注用；查不到则回退「#id」）
     * @param selected    用户勾选的「我有的食材」集合
     */
    public List<DishMatch> rankDishes(List<DishIngredient> all,
                                      Map<Long, Dish> dishById,
                                      Map<Long, String> ingredientName,
                                      Set<Long> selected) {
        // dishId → 该菜的全部食材 id
        Map<Long, Set<Long>> dishToIngs = all.stream().collect(Collectors.groupingBy(
                DishIngredient::getDishId,
                Collectors.mapping(DishIngredient::getIngredientId, Collectors.toSet())));

        List<DishMatch> out = new ArrayList<>();
        for (Map.Entry<Long, Set<Long>> e : dishToIngs.entrySet()) {
            Long dishId = e.getKey();
            Dish dish = dishById.get(dishId);
            if (dish == null) {
                continue;  // 菜已删，跳过
            }
            Set<Long> required = e.getValue();
            if (required.isEmpty()) {
                continue;  // 没录食材，无法判定，跳过
            }
            int matched = (int) required.stream().filter(selected::contains).count();
            if (matched == 0) {
                continue;  // 完全无交集
            }
            List<String> missing = required.stream()
                    .filter(id -> !selected.contains(id))
                    .map(id -> ingredientName.getOrDefault(id, "#" + id))
                    .sorted()
                    .toList();
            int missingCount = required.size() - matched;
            if (missingCount > MAX_MISSING) {
                continue;  // 缺太多，差太远，不推荐
            }
            out.add(new DishMatch(dish, matched, required.size(), missing, missingCount == 0));
        }
        // 排序：① 可做（canMake）优先 ② 缺得越少越靠前 ③ 食材总数越少越靠前（利于清库存）
        out.sort(Comparator
                .comparing(DishMatch::canMake, Comparator.reverseOrder())
                .thenComparingInt(DishMatch::missingCount)
                .thenComparingInt(DishMatch::totalCount));
        return out;
    }

    /** 单道菜与所选食材的匹配结果。 */
    public record DishMatch(Dish dish,
                            int matchCount,        // 命中食材数
                            int totalCount,        // 该菜所需食材总数
                            List<String> missingIngredients,  // 缺失食材名（可做时为空）
                            boolean canMake) {     // 全匹配即可做
        /** 缺失数 = 总数 - 命中数。供排序用。 */
        public int missingCount() {
            return totalCount - matchCount;
        }
    }
}
