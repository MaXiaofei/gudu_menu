package com.gudu.xsd.modules.dish;

import com.gudu.xsd.common.BizException;
import com.gudu.xsd.common.R;
import com.gudu.xsd.modules.dish.DishService.DishDetail;
import com.gudu.xsd.modules.member.MpPerm;
import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/dish")
@RequiredArgsConstructor
@Tag(name = "菜品")
public class DishController {

    private final DishService svc;
    private final DishQueryService querySvc;
    private final RecipeImporter recipeImporter;
    private final DishVectorService vectorSvc;

    /** 语义找菜请求：自然语言 query + 相似候选数/难度/时长过滤（均可选）。 */
    @lombok.Data
    public static class SemanticSearchReq {
        private String query;
        private Integer topK;
        private Integer maxDifficulty;
        private Integer maxMinutes;
    }

    /** 语义找菜结果项。 */
    public record SemanticHit(Long dishId, String name, Double score,
                              Integer difficulty, Integer cookTime) {}

    /** 语义找菜：自然语言（「清淡下饭」「酸甜口」）→ 向量相似召回菜谱。 */
    @PostMapping("/semantic-search")
    public R<List<SemanticHit>> semanticSearch(@RequestBody SemanticSearchReq req) {
        if (req.getQuery() == null || req.getQuery().isBlank()) {
            throw new BizException("请输入想吃的描述");
        }
        List<SemanticHit> hits = vectorSvc.semanticSearch(
                        req.getQuery().trim(), req.getTopK(),
                        req.getMaxDifficulty(), req.getMaxMinutes())
                .stream()
                .map(d -> new SemanticHit(
                        ((Number) d.getMetadata().get("dishId")).longValue(),
                        (String) d.getMetadata().get("name"),
                        d.getScore(),
                        d.getMetadata().get("difficulty") == null ? null
                                : ((Number) d.getMetadata().get("difficulty")).intValue(),
                        d.getMetadata().get("cookTime") == null ? null
                                : ((Number) d.getMetadata().get("cookTime")).intValue()))
                .toList();
        return R.ok(hits);
    }

    /** 存量菜谱向量重建（部署后建库 / 数据修复用，分批 embedding）。 */
    @PostMapping("/vector/rebuild")
    public R<Integer> rebuildVectors() {
        return R.ok(vectorSvc.rebuildAll());
    }

    @GetMapping
    public R<List<Dish>> list() {
        return R.ok(svc.list());
    }

    /** 多维搜索分页（放在 /{id} 之前，避免被路径变量吞掉）。 */
    @GetMapping("/search")
    public R<IPage<Dish>> search(DishSearchDTO q) {
        return R.ok(svc.search(q));
    }

    /** 营养筛选（POST，@RequestBody 支持 nutritionLimits JSON body；GET 无法绑 Map<Long,BigDecimal>）。 */
    @PostMapping("/search")
    public R<IPage<Dish>> searchByNutrition(@RequestBody DishSearchDTO q) {
        return R.ok(svc.search(q));
    }

    @GetMapping("/{id}")
    public R<DishDetail> detail(@PathVariable Long id) {
        return R.ok(svc.detail(id));
    }

    /** 份数营养：复用 NutritionCalcService。 */
    @GetMapping("/{id}/nutrition")
    public R<Map<Long, BigDecimal>> nutrition(@PathVariable Long id,
                                              @RequestParam(required = false) BigDecimal serving) {
        return R.ok(querySvc.nutrition(id, serving));
    }

    @GetMapping("/{id}/history")
    public R<List<DishHistory>> history(@PathVariable Long id) {
        return R.ok(querySvc.history(id));
    }

    @DeleteMapping("/{id}/history/{hid}")
    public R<?> deleteHistory(@PathVariable Long id, @PathVariable Long hid) {
        querySvc.deleteHistory(id, hid);
        return R.ok(null);
    }

    @DeleteMapping("/{id}/history")
    public R<?> clearHistory(@PathVariable Long id) {
        querySvc.clearHistory(id);
        return R.ok(null);
    }

    @PostMapping
    @MpPerm("dish.create")
    public R<?> save(@RequestBody DishSaveDTO dto) {
        svc.saveFull(dto);
        return R.ok(dto.getDish().getId());
    }

    /** URL 导入菜谱：抓网页 → 解析 → 落库，返回新菜品 id。 */
    @PostMapping("/import-url")
    @MpPerm("dish.create")
    public R<Long> importUrl(@RequestParam String url) {
        try {
            DishSaveDTO dto = recipeImporter.importFromUrl(url);
            svc.saveFull(dto);
            return R.ok(dto.getDish().getId());
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("import-url 失败 url={} err={}", url, e.toString());
            throw new BizException("导入失败，请检查链接是否正确或稍后重试");
        }
    }

    /** 更新：先存历史快照，再保存（Controller 编排，避开 Service 循环依赖）。 */
    @PutMapping
    @MpPerm("dish.edit")
    public R<?> update(@RequestBody DishSaveDTO dto) {
        if (dto.getDish().getId() != null) {
            querySvc.snapshotBeforeUpdate(dto.getDish().getId());
        }
        svc.saveFull(dto);
        return R.ok(null);
    }

    @DeleteMapping("/{id}")
    public R<?> del(@PathVariable Long id) {
        svc.deleteFull(id);
        return R.ok(null);
    }
}
