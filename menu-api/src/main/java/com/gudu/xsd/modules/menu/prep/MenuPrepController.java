package com.gudu.xsd.modules.menu.prep;

import com.gudu.xsd.common.R;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 备菜接口（备菜模块 Plan C）。
 *
 * <ul>
 *   <li>GET  /menu/{id}/prep                — 聚合备料列表（主料/调料分组 + 状态 + 共用 + 进度）。</li>
 *   <li>PUT  /menu/{id}/prep/{ingredientId} — 更新某食材备料状态（?status=READY/THAWING/MARINATING/PENDING）。</li>
 * </ul>
 */
@RestController
@RequestMapping("/menu")
@RequiredArgsConstructor
@Tag(name = "备菜")
public class MenuPrepController {

    private final MenuPrepService svc;

    /** GET /menu/{id}/prep：聚合备料列表 + 状态 + 进度。 */
    @GetMapping("/{id}/prep")
    public R<MenuPrepVO> getPrep(@PathVariable Long id) {
        return R.ok(svc.getPrep(id));
    }

    /** PUT /menu/{id}/prep/{ingredientId}?status=READY：更新备料状态。 */
    @PutMapping("/{id}/prep/{ingredientId}")
    public R<?> updateStatus(@PathVariable Long id, @PathVariable Long ingredientId,
                             @RequestParam PrepStatus status) {
        svc.updateStatus(id, ingredientId, status);
        return R.ok(null);
    }
}
