package com.gudu.xsd.modules.feedback.interfaces;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gudu.xsd.common.R;
import com.gudu.xsd.common.security.RequireAdmin;
import com.gudu.xsd.modules.feedback.application.FeedbackAppService;
import com.gudu.xsd.modules.feedback.application.FeedbackView;
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

import java.util.List;

/** 意见反馈（G14）：登录用户提交；超管查看与标记处理。 */
@RestController
@RequestMapping("/feedback")
@RequiredArgsConstructor
@Tag(name = "意见反馈")
public class FeedbackController {

    private final FeedbackAppService appService;

    @Data
    public static class SubmitReq {
        private String content;
        private String contact;
        private List<String> images;
        private String clientInfo;
    }

    @Data
    public static class StatusReq {
        private String status;
    }

    @PostMapping
    public R<Long> submit(@RequestBody SubmitReq req) {
        return R.ok(appService.submit(StpUtil.getLoginIdAsLong(), req.getContent(), req.getContact(),
                req.getImages(), req.getClientInfo()));
    }

    @GetMapping
    @RequireAdmin
    public R<IPage<FeedbackView>> page(@RequestParam(required = false) String status,
                                       @RequestParam(required = false) Integer pageNum,
                                       @RequestParam(required = false) Integer pageSize) {
        return R.ok(appService.page(status, pageNum, pageSize));
    }

    @PutMapping("/{id}/status")
    @RequireAdmin
    public R<Void> changeStatus(@PathVariable Long id, @RequestBody StatusReq req) {
        appService.changeStatus(id, req.getStatus());
        return R.ok(null);
    }
}
