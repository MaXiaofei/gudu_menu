package com.gudu.xsd.modules.menu;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gudu.xsd.common.PageQuery;
import com.gudu.xsd.common.R;
import com.gudu.xsd.modules.menu.MenuService.MenuDetail;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/menu")
@RequiredArgsConstructor
@Tag(name = "菜单")
public class MenuController {

    private final MenuService svc;

    @GetMapping
    public R<IPage<Menu>> list(PageQuery q) {
        return R.ok(svc.page(q));
    }

    @GetMapping("/{id}")
    public R<MenuDetail> detail(@PathVariable Long id) {
        return R.ok(svc.detail(id));
    }

    /** 菜单汇总：总价 + 营养（TDD 纯函数 MenuCalcService）。 */
    @GetMapping("/{id}/summary")
    public R<MenuService.MenuSummary> summary(@PathVariable Long id) {
        return R.ok(svc.summary(id));
    }

    @PostMapping
    public R<?> save(@RequestBody MenuSaveDTO dto) {
        svc.saveWithDishes(dto);
        return R.ok(dto.getMenu().getId());
    }

    @PutMapping
    public R<?> update(@RequestBody MenuSaveDTO dto) {
        svc.saveWithDishes(dto);
        return R.ok(null);
    }

    @DeleteMapping("/{id}")
    public R<?> del(@PathVariable Long id) {
        svc.removeById(id);
        return R.ok(null);
    }
}
