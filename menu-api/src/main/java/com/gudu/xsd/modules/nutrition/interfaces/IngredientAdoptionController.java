package com.gudu.xsd.modules.nutrition.interfaces;

import cn.dev33.satoken.stp.StpUtil;
import com.gudu.xsd.common.R;
import com.gudu.xsd.common.security.RequireOperator;
import com.gudu.xsd.modules.nutrition.application.IngredientAdoptionAppService;
import com.gudu.xsd.modules.nutrition.application.dto.IngredientAdoptionViews.AdoptResult;
import com.gudu.xsd.modules.nutrition.application.dto.IngredientAdoptionViews.AliasItem;
import com.gudu.xsd.modules.nutrition.application.dto.IngredientAdoptionViews.Group;
import com.gudu.xsd.modules.nutrition.application.dto.IngredientAdoptionViews.PageResult;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 运营收编：家庭新建食材汇总、收编 / 合并、别名维护（仅平台运营，写操作落操作日志）。 */
@RestController
@RequestMapping("/admin/ingredient-adoption")
@RequiredArgsConstructor
@RequireOperator
@Tag(name = "食材收编（运营）")
public class IngredientAdoptionController {

    private final IngredientAdoptionAppService app;

    @Data
    public static class AdoptReq {
        /** 汇总行的规整名称。 */
        private String groupName;
        /** 运营确认的公共食材名称（默认同 groupName）。 */
        private String name;
        private Long purchaseCategoryId;
        private String reason;
    }

    @Data
    public static class MergeReq {
        private String groupName;
        private Long targetId;
        private String reason;
    }

    @Data
    public static class AliasReq {
        private String alias;
        private Long ingredientId;
    }

    /** 家庭私有食材按规整名称汇总（家庭数降序）。 */
    @GetMapping("/groups")
    public R<List<Group>> groups() {
        return R.ok(app.groups());
    }

    /** 操作 A：收编为公共食材。 */
    @PostMapping("/adopt")
    public R<AdoptResult> adopt(@RequestBody AdoptReq req) {
        return R.ok(app.adopt(StpUtil.getLoginIdAsLong(), req.getGroupName(), req.getName(),
                req.getPurchaseCategoryId(), req.getReason()));
    }

    /** 操作 B：合并到已有公共食材。 */
    @PostMapping("/merge")
    public R<AdoptResult> merge(@RequestBody MergeReq req) {
        return R.ok(app.mergeInto(StpUtil.getLoginIdAsLong(), req.getGroupName(), req.getTargetId(), req.getReason()));
    }

    @GetMapping("/aliases")
    public R<PageResult<AliasItem>> aliases(@RequestParam(required = false) String keyword,
                                            @RequestParam(defaultValue = "1") int pageNum,
                                            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(app.aliasPage(keyword, pageNum, pageSize));
    }

    @PostMapping("/aliases")
    public R<AliasItem> addAlias(@RequestBody AliasReq req) {
        return R.ok(app.addAlias(StpUtil.getLoginIdAsLong(), req.getAlias(), req.getIngredientId()));
    }

    @DeleteMapping("/aliases/{id}")
    public R<?> deleteAlias(@PathVariable Long id) {
        app.deleteAlias(StpUtil.getLoginIdAsLong(), id);
        return R.ok(null);
    }
}
