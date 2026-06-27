package com.gudu.xsd.modules.notification;

import cn.dev33.satoken.stp.StpUtil;
import com.gudu.xsd.common.BizException;
import com.gudu.xsd.common.R;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/notification")
@RequiredArgsConstructor
@Tag(name = "通知中心")
public class NotificationController {

    private final NotificationService svc;
    private final NotificationScheduler scheduler;

    @GetMapping
    public R<List<Notification>> list() {
        return R.ok(svc.list(currentMemberId()));
    }

    /** 手动触发临期扫描（不等 @Scheduled），便于 E2E/演示。返回发送条数。 */
    @PostMapping("/scan-expiring")
    public R<Map<String, Object>> scanExpiring(@RequestParam(defaultValue = "3") int days) {
        int sent = scheduler.checkExpiring(days);
        return R.ok(Map.of("sent", sent));
    }

    @GetMapping("/unread-count")
    public R<Map<String, Object>> unreadCount() {
        return R.ok(Map.of("count", svc.unreadCount(currentMemberId())));
    }

    @PutMapping("/{id}/read")
    public R<?> markRead(@PathVariable Long id) {
        svc.markRead(id, currentMemberId());
        return R.ok(null);
    }

    private Long currentMemberId() {
        Long id = StpUtil.getSession().getLong("currentMemberId");
        if (id == null) throw new BizException("请先选择就餐成员");
        return id;
    }
}
