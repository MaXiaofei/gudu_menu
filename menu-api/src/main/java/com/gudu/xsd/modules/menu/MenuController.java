package com.gudu.xsd.modules.menu;

import com.baomidou.mybatisplus.core.metadata.IPage;
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
    public R<IPage<Menu>> list(MenuPageQuery q) {
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

    /** 修改/删除食集中某道菜的备注（note 空串=删除备注，原型菜行「加备注/忌口…」）。 */
    public record NoteReq(String note) {}

    @PutMapping("/{menuId}/dish/{dishId}/note")
    public R<?> updateDishNote(@PathVariable Long menuId, @PathVariable Long dishId,
                               @RequestBody NoteReq req) {
        svc.updateDishNote(menuId, dishId, req == null ? null : req.note());
        return R.ok(null);
    }

    /** 从食集中移除某道菜（原型菜行行尾 ✕）。 */
    @DeleteMapping("/{menuId}/dish/{dishId}")
    public R<?> removeDish(@PathVariable Long menuId, @PathVariable Long dishId) {
        svc.removeDish(menuId, dishId);
        return R.ok(null);
    }

    /** 一起吃 tab 汇总数量（占位，协同点菜待建）。 */
    @GetMapping("/{id}/together-count")
    public R<Integer> togetherCount(@PathVariable Long id) {
        return R.ok(svc.getTogetherCount(id));
    }

    /** 再做一次（食记单条详情）：复制食集 → 新 ACTIVE 食集，返回新 id。 */
    @PostMapping("/{id}/copy")
    public R<Long> copy(@PathVariable Long id) {
        return R.ok(svc.copyMenu(id, currentMemberId()));
    }

    private Long currentMemberId() {
        try {
            return cn.dev33.satoken.stp.StpUtil.getLoginIdAsLong();
        } catch (Exception e) {
            return null;
        }
    }

    @DeleteMapping("/{id}")
    public R<?> del(@PathVariable Long id) {
        svc.removeById(id);
        return R.ok(null);
    }
}
