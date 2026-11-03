package com.gudu.xsd.modules.dish.interfaces;

import cn.dev33.satoken.stp.StpUtil;
import com.gudu.xsd.common.R;
import com.gudu.xsd.common.security.RequireOperator;
import com.gudu.xsd.modules.dish.application.DishLibraryAppService;
import com.gudu.xsd.modules.dish.application.dto.DishLibraryViews.PageResult;
import com.gudu.xsd.modules.dish.application.dto.DishLibraryViews.Row;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 公共菜谱库运营：列表（按上架状态筛选）、下架 / 重新上架（仅平台运营，写操作落操作日志）。 */
@RestController
@RequestMapping("/admin/dish-library")
@RequiredArgsConstructor
@RequireOperator
@Tag(name = "公共菜谱库（运营）")
public class DishLibraryAdminController {

    private final DishLibraryAppService app;

    @Data
    public static class ReasonReq {
        private String reason;
    }

    /** status：online / offline / 空=全部。 */
    @GetMapping
    public R<PageResult> page(@RequestParam(required = false) String keyword,
                              @RequestParam(required = false) String status,
                              @RequestParam(defaultValue = "1") int pageNum,
                              @RequestParam(defaultValue = "15") int pageSize) {
        return R.ok(app.page(keyword, status, pageNum, pageSize));
    }

    @PostMapping("/{id}/offline")
    public R<Row> offline(@PathVariable Long id, @RequestBody ReasonReq req) {
        return R.ok(app.offline(StpUtil.getLoginIdAsLong(), id, req == null ? null : req.getReason()));
    }

    @PostMapping("/{id}/online")
    public R<Row> online(@PathVariable Long id, @RequestBody(required = false) ReasonReq req) {
        return R.ok(app.online(StpUtil.getLoginIdAsLong(), id, req == null ? null : req.getReason()));
    }
}
