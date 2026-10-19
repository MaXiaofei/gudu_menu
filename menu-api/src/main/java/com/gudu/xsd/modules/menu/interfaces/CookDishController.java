package com.gudu.xsd.modules.menu.interfaces;

import cn.dev33.satoken.stp.StpUtil;
import com.gudu.xsd.common.R;
import com.gudu.xsd.modules.menu.CookMaterialsVO;
import com.gudu.xsd.modules.menu.application.CookAppService;
import com.gudu.xsd.modules.menu.application.CookDishResult;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * 单菜直做（G1）：不建食集直接做一道菜 → 写食记 + 按确认回写余量。
 * 身份一律取登录账号（StpUtil），不接受客户端传 memberId。
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "做菜")
public class CookDishController {

    private final CookAppService appService;

    @Data
    public static class CookDishReq {
        private BigDecimal servings;
        private List<Long> usedUp;
        private List<Long> partiallyUsed;
        @Size(max = 200, message = "备注最多 200 字")
        private String note;
    }

    /** 做菜确认弹窗数据（结构同 GET /menu/{id}/cook-materials，menuId=null）。 */
    @GetMapping("/cook/dish/{dishId}/materials")
    public R<CookMaterialsVO> materials(@PathVariable Long dishId,
                                        @RequestParam(required = false) BigDecimal servings) {
        return R.ok(appService.dishMaterials(dishId, servings));
    }

    /** 单菜直做：写 cooking_record(source=dish)，用完→没有、用了一些→降一档。 */
    @PostMapping("/cook/dish/{dishId}")
    public R<CookDishResult> cook(@PathVariable Long dishId,
                                  @RequestBody(required = false) @Valid CookDishReq req) {
        CookDishReq r = req == null ? new CookDishReq() : req;
        return R.ok(appService.cookDish(dishId, StpUtil.getLoginIdAsLong(), r.getServings(),
                r.getUsedUp(), r.getPartiallyUsed(), r.getNote()));
    }
}
