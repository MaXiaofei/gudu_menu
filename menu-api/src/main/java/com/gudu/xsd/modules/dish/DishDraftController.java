package com.gudu.xsd.modules.dish;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gudu.xsd.common.PageQuery;
import com.gudu.xsd.common.R;
import com.gudu.xsd.modules.dish.DishDraftDTO.Detail;
import com.gudu.xsd.modules.dish.DishDraftDTO.ListItem;
import com.gudu.xsd.modules.dish.DishDraftDTO.Save;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 写菜谱草稿（DESIGN.md §16.4）：存草稿不校验必填；草稿箱列表/恢复编辑/删除。
 * 路径避开 /dish/{id} 冲突：list 用 /dish/draft/list（多一段），其余按段数天然不冲突。
 */
@Slf4j
@RestController
@RequestMapping("/dish/draft")
@RequiredArgsConstructor
@Tag(name = "菜品草稿")
public class DishDraftController {

    private final DishDraftService svc;

    /** 保存草稿（id 空 = 新建，返回草稿 id；非空 = 更新）。 */
    @PostMapping
    public R<Long> save(@RequestBody Save dto) {
        return R.ok(svc.save(dto));
    }

    /** 草稿箱列表（本人，按更新时间倒序；分页，DESIGN.md §12）。 */
    @GetMapping("/list")
    public R<IPage<ListItem>> list(PageQuery q) {
        return R.ok(svc.list(q.getPageNum(), q.getPageSize()));
    }

    /** 草稿详情（恢复编辑回填）。 */
    @GetMapping("/{id}")
    public R<Detail> detail(@PathVariable Long id) {
        return R.ok(svc.detail(id));
    }

    /** 删除草稿（本人；发布成功后前端调用清掉）。 */
    @DeleteMapping("/{id}")
    public R<?> delete(@PathVariable Long id) {
        svc.delete(id);
        return R.ok(null);
    }
}
