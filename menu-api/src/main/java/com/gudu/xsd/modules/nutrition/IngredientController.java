package com.gudu.xsd.modules.nutrition;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gudu.xsd.common.BizException;
import com.gudu.xsd.common.R;
import com.gudu.xsd.common.security.RequireOperator;
import com.gudu.xsd.modules.contentsecurity.application.ContentSecurityAppService;
import com.gudu.xsd.modules.contentsecurity.domain.model.ContentPurpose;
import com.gudu.xsd.modules.dict.CatalogWriteGuard;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/ingredient")
@RequiredArgsConstructor
@Tag(name = "食材库")
public class IngredientController {

    private final IngredientService svc;
    private final CatalogWriteGuard guard;
    private final ContentSecurityAppService contentSecurity;

    @GetMapping
    public R<IPage<IngredientVO>> list(IngredientPageQuery q) {
        return R.ok(svc.pageWithNutrition(q));
    }

    /** 食材详情（编辑页：名称/默认单位/单价/品类/食用属性/换算数 + 营养）。 */
    @GetMapping("/{id}")
    public R<IngredientVO> detail(@PathVariable Long id) {
        return R.ok(svc.detail(id));
    }

    /** 该食材营养：metricId -> value(per 100g)。 */
    @GetMapping("/{id}/nutrition")
    public R<Map<Long, BigDecimal>> nutrition(@PathVariable Long id) {
        return R.ok(svc.nutritionOf(id));
    }

    @PostMapping
    public R<?> add(@RequestBody IngredientSaveDTO dto) {
        if (!guard.isOperator()) {
            // 全局食材库所有家庭共用：普通用户只能新建（忽略 id 与营养），同名返回已有
            Ingredient in = dto == null ? null : dto.getIngredient();
            String name = in == null || in.getName() == null ? "" : in.getName().trim();
            if (name.isEmpty()) throw new BizException("食材名称不能为空");
            contentSecurity.assertUserText(ContentPurpose.CATALOG_NAME, name);
            Ingredient exist = svc.findByName(name);
            if (exist != null) return R.ok(exist.getId());
            in.setId(null);
            in.setName(name);
            in.setPurchaseCount(null);
            in.setUsageCount(null);
            in.setDeleted(null);
            svc.saveWithNutrition(in, java.util.List.of());
            return R.ok(in.getId());
        }
        svc.saveWithNutrition(dto.getIngredient(), dto.getNutritions());
        return R.ok(dto.getIngredient().getId());
    }

    @PutMapping
    @RequireOperator
    public R<?> update(@RequestBody Ingredient ing) {
        svc.updateById(ing);
        return R.ok(null);
    }

    @DeleteMapping("/{id}")
    @RequireOperator
    public R<?> del(@PathVariable Long id) {
        svc.removeById(id);
        return R.ok(null);
    }
}
