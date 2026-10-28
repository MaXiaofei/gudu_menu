package com.gudu.xsd.modules.nutrition;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gudu.xsd.common.BizException;
import com.gudu.xsd.common.R;
import com.gudu.xsd.modules.dict.CatalogWriteGuard;
import com.gudu.xsd.modules.nutrition.application.IngredientCatalogAppService;
import com.gudu.xsd.modules.nutrition.domain.service.IngredientNameNormalizer;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 食材库。归属规则（V95）：公共食材（family_id NULL）只有管理后台运营能改 / 删；
 * 家庭用户新建的食材是本家私有（别家不可见），本家成员可改名 / 分类 / 食用属性，未被使用才可删。
 * 列表 / 搜索 / 详情返回「公共 + 本家」，搜索匹配别名，每条带 familyOwned。
 */
@RestController
@RequestMapping("/ingredient")
@RequiredArgsConstructor
@Tag(name = "食材库")
public class IngredientController {

    private final IngredientService svc;
    private final CatalogWriteGuard guard;
    private final IngredientCatalogAppService catalog;

    /** 列表 / 搜索：keyword 匹配名称与别名；familyOnly=true 只看「我家」食材。 */
    @GetMapping
    public R<IPage<IngredientVO>> list(IngredientPageQuery q) {
        return R.ok(svc.pageWithNutrition(q));
    }

    /** 食材详情（编辑页：名称/品类/食用属性 + 营养）；别家私有按不存在处理（返回 null）。 */
    @GetMapping("/{id}")
    public R<IngredientVO> detail(@PathVariable Long id) {
        return R.ok(svc.detail(id));
    }

    /** 该食材营养：metricId -> value(per 100g)。别家私有食材无权读，返回空。 */
    @GetMapping("/{id}/nutrition")
    public R<Map<Long, BigDecimal>> nutrition(@PathVariable Long id) {
        if (svc.getById(id) == null) return R.ok(Map.of());
        return R.ok(svc.nutritionOf(id));
    }

    /**
     * 新建。管理后台运营 -> 公共食材（含营养）；其他人 -> 名称规整后与公共名 / 别名一致返回该公共食材 id，
     * 与本家已有一致返回已有 id，否则新建本家私有食材（忽略营养）。data 恒为食材 id（App 兼容）；
     * 命中已有公共食材时 msg 为「已有公共食材：马铃薯」。
     */
    @PostMapping
    public R<?> add(@RequestBody IngredientSaveDTO dto) {
        if (guard.canMaintainPublic()) {
            Ingredient in = dto == null ? null : dto.getIngredient();
            if (in == null) throw new BizException("食材不能为空");
            String name = IngredientNameNormalizer.normalize(in.getName());
            if (name.isEmpty()) throw new BizException("食材名称不能为空");
            in.setId(null);
            in.setName(name);
            in.setFamilyId(null);
            svc.saveWithNutrition(in, dto.getNutritions() == null ? List.of() : dto.getNutritions());
            return R.ok(in.getId());
        }
        IngredientCatalogAppService.Resolved r = catalog.resolveOrCreate(dto == null ? null : dto.getIngredient());
        R<Long> res = R.ok(r.id());
        if (r.tip() != null) res.setMsg(r.tip());
        return res;
    }

    /** 小程序用：新建 / 去重并返回完整结果（id、name、existed=public|family|created、familyOwned），便于提示「已有：马铃薯」。 */
    @PostMapping("/resolve")
    public R<IngredientCatalogAppService.Resolved> resolve(@RequestBody Ingredient in) {
        return R.ok(catalog.resolveOrCreate(in));
    }

    /** 修改：公共食材仅管理后台运营；本家私有食材本家成员可改名 / 分类 / 食用属性。 */
    @PutMapping
    public R<?> update(@RequestBody Ingredient ing) {
        catalog.update(ing);
        return R.ok(null);
    }

    /** 删除：公共食材仅管理后台运营；本家私有食材被使用时拒绝。 */
    @DeleteMapping("/{id}")
    public R<?> del(@PathVariable Long id) {
        catalog.delete(id);
        return R.ok(null);
    }
}
