package com.yanhuo.xsd.modules.menu;

import com.yanhuo.xsd.common.R;
import com.yanhuo.xsd.modules.menu.MenuService.MenuDetail;
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
    public R<List<Menu>> list() {
        return R.ok(svc.list());
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
