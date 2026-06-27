package com.gudu.xsd.modules.dict;

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

    private final DictService svc;

    @GetMapping
    public R<IPage<SysDict>> list(@RequestParam String group, PageQuery q) {
        return R.ok(svc.pageByGroup(group, q));
    }

    @PostMapping
    public R<?> add(@RequestBody SysDict d) {
        svc.save(d);
        return R.ok(d.getId());
    }

    @PutMapping
    public R<?> update(@RequestBody SysDict d) {
        svc.updateById(d);
        return R.ok(null);
    }

    @DeleteMapping("/{id}")
    public R<?> del(@PathVariable Long id) {
        svc.removeById(id);
        return R.ok(null);
    }
}
