package com.gudu.xsd.modules.notification;

import cn.dev33.satoken.stp.StpUtil;
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
        return R.ok(svc.list(account(), profile()));
    }

    /** 手动触发临期扫描（不等 @Scheduled），便于 E2E/演示。返回发送条数。 */
    @PostMapping("/scan-expiring")
    public R<Map<String, Object>> scanExpiring(@RequestParam(defaultValue = "3") int days) {
        int sent = scheduler.checkExpiring(days);
        return R.ok(Map.of("sent", sent));
    }

    @GetMapping("/unread-count")
    public R<Map<String, Object>> unreadCount() {
        return R.ok(Map.of("count", svc.unreadCount(account(), profile())));
    }

    @PutMapping("/{id}/read")
    public R<?> markRead(@PathVariable Long id) {
        svc.markRead(id, account(), profile());
        return R.ok(null);
    }

    /** 登录账号 id（account.id）：账号级消息（反馈回复、厨房圈互动）的收件人。 */
    private static Long account() {
        return StpUtil.getLoginIdAsLong();
    }

    /**
     * 当前就餐档案 id（member.id）：档案级消息（临期、采购）的收件人；可空。
     * 与账号 id 分开传给 service，各比各的列——两种 id 数值相同也不会串号。
     */
    private static Long profile() {
        return com.gudu.xsd.common.security.CurrentMember.id();
    }
}
