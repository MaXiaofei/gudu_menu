package com.gudu.xsd.modules.dish;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gudu.xsd.modules.dict.SysDict;
import com.gudu.xsd.modules.dict.mapper.DictMapper;
import com.gudu.xsd.modules.dish.mapper.DishDictMapper;
import com.gudu.xsd.modules.dish.mapper.DishIngredientMapper;
import com.gudu.xsd.modules.dish.mapper.DishMapper;
import com.gudu.xsd.modules.dish.mapper.DishStepMapper;
import com.gudu.xsd.modules.nutrition.Ingredient;
import com.gudu.xsd.modules.nutrition.mapper.IngredientMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 菜谱向量库（PGVector + Ollama bge-m3）：菜谱文本向量化写入 + 语义相似检索。
 *
 * <p>文档约定：id = {@code dish-{dishId}}；文本 = 菜名 + 菜系/标签 + 食材清单 + 步骤摘要 + 简介；
 * metadata 带 dishId/name/difficulty/cookTime（检索时可 filterExpression 条件过滤）。
 *
 * <p>写入时机：{@code DishService.saveFull/deleteFull} 后同步 upsert/remove（导入同走 saveFull 自动覆盖）；
 * 存量由 {@link #rebuildAll()} 分批重建。向量写入失败不阻断菜谱保存（旁路，仅 warn）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DishVectorService {

    private final VectorStore vectorStore;
    private final EmbeddingModel embeddingModel;
    private final DishMapper dishMapper;
    private final DishStepMapper stepMapper;
    private final DishDictMapper dictRelMapper;
    private final DishIngredientMapper dishIngMapper;
    private final IngredientMapper ingredientMapper;
    private final DictMapper dictMapper;

    private static String docId(Long dishId) {
        return "dish-" + dishId;
    }

    /** 菜谱保存/更新后同步向量（先删后写实现 upsert；失败不阻断主流程）。 */
    public void upsertDish(Long dishId) {
        try {
            Dish dish = dishMapper.selectById(dishId);
            if (dish == null) {
                removeDish(dishId);
                return;
            }
            Document doc = buildDocument(dish);
            vectorStore.delete(List.of(docId(dishId)));
            vectorStore.add(List.of(doc));
        } catch (Exception e) {
            log.warn("菜谱向量同步失败 dishId={} err={}", dishId, e.toString());
        }
    }

    /** 菜谱删除后移除向量（失败不阻断）。 */
    public void removeDish(Long dishId) {
        try {
            vectorStore.delete(List.of(docId(dishId)));
        } catch (Exception e) {
            log.warn("菜谱向量删除失败 dishId={} err={}", dishId, e.toString());
        }
    }

    /** 存量全量重建（分批，每批 50 道；返回成功写入数）。 */
    public int rebuildAll() {
        List<Long> ids = dishMapper.selectList(new QueryWrapper<Dish>()
                        .select("id").orderByAsc("id")).stream()
                .map(Dish::getId).toList();
        int ok = 0;
        final int batch = 50;
        for (int i = 0; i < ids.size(); i += batch) {
            List<Long> part = ids.subList(i, Math.min(ids.size(), i + batch));
            for (Long id : part) {
                try {
                    Dish dish = dishMapper.selectById(id);
                    if (dish == null) continue;
                    vectorStore.delete(List.of(docId(id)));
                    vectorStore.add(List.of(buildDocument(dish)));
                    ok++;
                } catch (Exception e) {
                    log.warn("重建向量失败 dishId={} err={}", id, e.toString());
                }
            }
        }
        log.info("菜谱向量重建完成 total={} ok={}", ids.size(), ok);
        return ok;
    }

    /**
     * 语义找菜：自然语言 query → 向量相似 TopK（可带难度/时长条件过滤）。
     * 返回 Document（metadata 含 dishId/name，score 为相似度）。
     */
    public List<Document> semanticSearch(String query, int topK,
                                         Integer maxDifficulty, Integer maxMinutes) {
        SearchRequest.Builder b = SearchRequest.builder()
                .query(query)
                .topK(topK <= 0 ? 10 : topK)
                .similarityThreshold(0.0);
        FilterExpressionBuilder feb = new FilterExpressionBuilder();
        if (maxDifficulty != null && maxMinutes != null) {
            b.filterExpression(feb.and(
                    feb.lte("difficulty", maxDifficulty),
                    feb.lte("cookTime", maxMinutes)).build());
        } else if (maxDifficulty != null) {
            b.filterExpression(feb.lte("difficulty", maxDifficulty).build());
        } else if (maxMinutes != null) {
            b.filterExpression(feb.lte("cookTime", maxMinutes).build());
        }
        return vectorStore.similaritySearch(b.build());
    }

    /** 单菜向量（口味画像聚合用）：返回该菜 embedding，无则 null。 */
    public float[] embeddingOf(Long dishId) {
        Dish dish = dishMapper.selectById(dishId);
        if (dish == null) return null;
        return embeddingModel.embed(buildDocument(dish).getText());
    }

    /** 用量文本：「2个」「适量」（amount + unit 名）。 */
    private String amountText(DishIngredient di) {
        if (di.getAmount() == null) {
            return di.getUnitName() != null ? di.getUnitName() : "";
        }
        String amt = di.getAmount().stripTrailingZeros().toPlainString();
        return di.getUnitName() == null ? amt : amt + di.getUnitName();
    }

    // ===================== 文档组装 =====================

    /** 组装菜谱语义文档：菜名 + 菜系/标签 + 食材清单 + 步骤摘要 + 简介。 */
    Document buildDocument(Dish dish) {
        Long id = dish.getId();
        StringBuilder sb = new StringBuilder();
        sb.append("菜名：").append(dish.getName()).append('\n');

        // 菜系/标签/分类名（dict join）
        List<DishDict> rels = dictRelMapper.selectList(
                new QueryWrapper<DishDict>().eq("dish_id", id));
        if (!rels.isEmpty()) {
            Set<Long> dictIds = rels.stream().map(DishDict::getDictId)
                    .filter(Objects::nonNull).collect(Collectors.toSet());
            Map<Long, String> names = dictIds.isEmpty() ? Map.of()
                    : dictMapper.selectBatchIds(dictIds).stream()
                            .collect(Collectors.toMap(SysDict::getId, SysDict::getName, (a, b) -> a));
            List<String> words = rels.stream()
                    .map(r -> names.get(r.getDictId()))
                    .filter(Objects::nonNull).toList();
            if (!words.isEmpty()) {
                sb.append("菜系标签：").append(String.join("、", words)).append('\n');
            }
        }

        // 食材清单（名称 + 用量文本）
        List<DishIngredient> ings = dishIngMapper.selectList(
                new QueryWrapper<DishIngredient>().eq("dish_id", id));
        if (!ings.isEmpty()) {
            Set<Long> ingIds = ings.stream().map(DishIngredient::getIngredientId)
                    .filter(Objects::nonNull).collect(Collectors.toSet());
            Map<Long, String> ingNames = ingIds.isEmpty() ? Map.of()
                    : ingredientMapper.selectBatchIds(ingIds).stream()
                            .collect(Collectors.toMap(Ingredient::getId, Ingredient::getName, (a, b) -> a));
            String line = ings.stream()
                    .map(di -> {
                        String n = di.getIngredientName() != null ? di.getIngredientName()
                                : ingNames.getOrDefault(di.getIngredientId(), "");
                        String amt = amountText(di);
                        return (n + " " + amt).trim();
                    })
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.joining("、"));
            if (!line.isEmpty()) {
                sb.append("食材：").append(line).append('\n');
            }
        }

        // 步骤摘要（前 3 步、每步截 60 字）
        List<DishStep> steps = stepMapper.selectList(
                new QueryWrapper<DishStep>().eq("dish_id", id).orderByAsc("sort_order", "seq"));
        if (!steps.isEmpty()) {
            String line = steps.stream().limit(3)
                    .map(s -> s.getText() == null ? "" : s.getText())
                    .filter(s -> !s.isBlank())
                    .map(s -> s.length() > 60 ? s.substring(0, 60) : s)
                    .collect(Collectors.joining("；"));
            if (!line.isEmpty()) {
                sb.append("做法：").append(line).append('\n');
            }
        }

        if (dish.getNote() != null && !dish.getNote().isBlank()) {
            sb.append("简介：").append(dish.getNote()).append('\n');
        }

        Map<String, Object> meta = new HashMap<>();
        meta.put("dishId", id);
        meta.put("name", dish.getName());
        if (dish.getDifficulty() != null) meta.put("difficulty", dish.getDifficulty());
        if (dish.getCookTime() != null) meta.put("cookTime", dish.getCookTime());
        return new Document(sb.toString(), meta);
    }
}
