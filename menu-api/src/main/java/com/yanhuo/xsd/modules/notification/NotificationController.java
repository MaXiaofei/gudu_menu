package com.yanhuo.xsd.modules.notification;

import cn.dev33.satoken.stp.StpUtil;
import com.yanhuo.xsd.common.R;
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

    @GetMapping
    public R<List<Notification>> list() {
        return R.ok(svc.list(currentMemberId()));
    }

    @GetMapping("/unread-count")
    public R<Map<String, Object>> unreadCount() {
        return R.ok(Map.of("count", svc.unreadCount(currentMemberId())));
    }

    @PutMapping("/{id}/read")
    public R<?> markRead(@PathVariable Long id) {
        svc.markRead(id);
        return R.ok(null);
    }

    private Long currentMemberId() {
        return StpUtil.getSession().getLong("currentMemberId");
    }
}
