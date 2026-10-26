package com.gudu.xsd.modules.community.interfaces;

import cn.dev33.satoken.stp.StpUtil;
import com.gudu.xsd.common.R;
import com.gudu.xsd.common.security.RequireAdmin;
import com.gudu.xsd.modules.community.application.CommunityAdminAppService;
import com.gudu.xsd.modules.community.application.CommunitySwitchAppService;
import com.gudu.xsd.modules.community.application.CommunitySwitchAppService.SwitchView;
import com.gudu.xsd.modules.community.application.dto.CommunityViews.AdminPostDetail;
import com.gudu.xsd.modules.community.application.dto.CommunityViews.AdminStats;
import com.gudu.xsd.modules.community.application.dto.CommunityViews.CursorPage;
import com.gudu.xsd.modules.community.application.dto.CommunityViews.QueueItem;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 社区审核后台（超管）。 */
@RestController
@RequestMapping("/admin/community")
@RequiredArgsConstructor
@RequireAdmin
@Tag(name = "社区审核")
public class CommunityAdminController {

    private final CommunityAdminAppService appService;
    private final CommunitySwitchAppService switchAppService;

    @Data
    public static class SwitchReq {
        private Boolean enabled;
        private String reason;
    }

    @Data
    public static class ModerateReq {
        private String action;
        private String reason;
        private String note;
    }

    @GetMapping("/queue")
    public R<CursorPage<QueueItem>> queue(@RequestParam(required = false) String status,
                                          @RequestParam(required = false) String risk,
                                          @RequestParam(required = false) String cursor,
                                          @RequestParam(required = false) Integer size) {
        return R.ok(appService.queue(status, risk, cursor, size));
    }

    @GetMapping("/posts/{id}")
    public R<AdminPostDetail> post(@PathVariable Long id) {
        return R.ok(appService.postDetail(id));
    }

    @GetMapping("/stats")
    public R<AdminStats> stats() {
        return R.ok(appService.stats());
    }

    @PostMapping("/posts/{id}/moderate")
    public R<Map<String, Object>> moderate(@PathVariable Long id, @RequestBody ModerateReq req) {
        appService.moderate(StpUtil.getLoginIdAsLong(), id, req.getAction(), req.getReason(), req.getNote());
        return R.ok(Map.of());
    }

    /** 厨房圈紧急关停开关（A.12）：当前状态 + 最后操作人 + 最近修改记录。 */
    @GetMapping("/switch")
    public R<SwitchView> switchState() {
        return R.ok(switchAppService.view());
    }

    /** 切换厨房圈开关（记录操作人与时间；≤30 秒全部实例生效）。 */
    @PutMapping("/switch")
    public R<SwitchView> setSwitch(@RequestBody SwitchReq req) {
        if (req == null || req.getEnabled() == null) return R.fail("enabled 不能为空");
        return R.ok(switchAppService.set(StpUtil.getLoginIdAsLong(), req.getEnabled(), req.getReason()));
    }
}
