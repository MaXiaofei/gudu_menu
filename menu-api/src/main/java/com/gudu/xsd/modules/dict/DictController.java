package com.gudu.xsd.modules.dict;

import com.gudu.xsd.common.BizException;
import com.gudu.xsd.common.security.RequireAdmin;
import com.gudu.xsd.modules.contentsecurity.application.ContentSecurityAppService;
import com.gudu.xsd.modules.contentsecurity.domain.model.ContentPurpose;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gudu.xsd.common.PageQuery;
import com.gudu.xsd.common.R;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/dict")
@RequiredArgsConstructor
@Tag(name = "配置中心")
public class DictController {

    /** 普通用户唯一允许新增的分组（小程序「自定义采购分类」）。 */
    static final String USER_WRITABLE_GROUP = "purchase_category";

    private final DictService svc;
    private final CatalogWriteGuard guard;
    private final ContentSecurityAppService contentSecurity;

    @GetMapping
    public R<IPage<SysDict>> list(@RequestParam String group, PageQuery q) {
        return R.ok(svc.pageByGroup(group, q));
    }

    @PostMapping
    public R<?> add(@RequestBody SysDict d) {
        if (!guard.isOperator()) {
            // 全局字典所有家庭共用：普通用户只能新增采购分类，且不能借 id 覆盖已有项
            if (d == null || !USER_WRITABLE_GROUP.equals(d.getDictGroup())) {
                throw BizException.forbidden("无权限：仅平台运营可维护该字典");
            }
            String name = d.getName() == null ? "" : d.getName().trim();
            if (name.isEmpty()) throw new BizException("名称不能为空");
            contentSecurity.assertUserText(ContentPurpose.CATALOG_NAME, name);
            List<SysDict> same = svc.listByGroup(USER_WRITABLE_GROUP).stream()
                    .filter(x -> name.equals(x.getName())).toList();
            if (!same.isEmpty()) return R.ok(same.get(0).getId());
            d.setId(null);
            d.setName(name);
            if (d.getSort() == null) d.setSort(0);
        }
        svc.save(d);
        return R.ok(d.getId());
    }

    @PutMapping
    @RequireAdmin
    public R<?> update(@RequestBody SysDict d) {
        svc.updateById(d);
        return R.ok(null);
    }

    @DeleteMapping("/{id}")
    @RequireAdmin
    public R<?> del(@PathVariable Long id) {
        svc.removeById(id);
        return R.ok(null);
    }
}
