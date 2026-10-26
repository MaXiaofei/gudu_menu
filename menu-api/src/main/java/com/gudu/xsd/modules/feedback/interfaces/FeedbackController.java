package com.gudu.xsd.modules.feedback.interfaces;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gudu.xsd.common.R;
import com.gudu.xsd.common.security.RequireOperator;
import com.gudu.xsd.modules.feedback.application.FeedbackAppService;
import com.gudu.xsd.modules.feedback.application.FeedbackSubmitCommand;
import com.gudu.xsd.modules.feedback.application.FeedbackView;
import com.gudu.xsd.modules.feedback.application.MyFeedbackView;
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

/** 意见反馈（G14 + A.11）：登录用户提交、看自己的反馈与回复；超管筛选、回复、标记处理。 */
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
        /** general / ai / complaint，空按 general。 */
        private String type;
        /** 来源引用（AI 消息 id、来源页面…）。 */
        private String sourceRef;
    }

    @Data
    public static class StatusReq {
        private String status;
    }

    @Data
    public static class ReplyReq {
        private String reply;
        /** 回复同时标记已处理。 */
        private Boolean markDone;
    }

    @PostMapping
    public R<Long> submit(@RequestBody SubmitReq req) {
        return R.ok(appService.submit(StpUtil.getLoginIdAsLong(), new FeedbackSubmitCommand(req.getContent(),
                req.getContact(), req.getImages(), req.getClientInfo(), req.getType(), req.getSourceRef())));
    }

    /** 我的反馈（提交人本人，按时间倒序）。 */
    @GetMapping("/mine")
    public R<IPage<MyFeedbackView>> mine(@RequestParam(required = false) Integer pageNum,
                                        @RequestParam(required = false) Integer pageSize) {
        return R.ok(appService.mine(StpUtil.getLoginIdAsLong(), pageNum, pageSize));
    }

    /** 我的反馈详情（非本人 404）。 */
    @GetMapping("/mine/{id}")
    public R<MyFeedbackView> mineDetail(@PathVariable Long id) {
        return R.ok(appService.mineDetail(StpUtil.getLoginIdAsLong(), id));
    }

    @GetMapping
    @RequireOperator
    public R<IPage<FeedbackView>> page(@RequestParam(required = false) String type,
                                       @RequestParam(required = false) String status,
                                       @RequestParam(required = false) Integer pageNum,
                                       @RequestParam(required = false) Integer pageSize) {
        return R.ok(appService.page(type, status, pageNum, pageSize));
    }

    @PutMapping("/{id}/reply")
    @RequireOperator
    public R<Void> reply(@PathVariable Long id, @RequestBody ReplyReq req) {
        appService.reply(StpUtil.getLoginIdAsLong(), id, req.getReply(), Boolean.TRUE.equals(req.getMarkDone()));
        return R.ok(null);
    }

    @PutMapping("/{id}/status")
    @RequireOperator
    public R<Void> changeStatus(@PathVariable Long id, @RequestBody StatusReq req) {
        appService.changeStatus(id, req.getStatus());
        return R.ok(null);
    }
}
