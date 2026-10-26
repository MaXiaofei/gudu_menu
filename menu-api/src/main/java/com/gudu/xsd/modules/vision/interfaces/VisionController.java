package com.gudu.xsd.modules.vision.interfaces;

import cn.dev33.satoken.stp.StpUtil;
import com.gudu.xsd.common.BizException;
import com.gudu.xsd.common.R;
import com.gudu.xsd.modules.contentsecurity.application.ContentSecurityAppService;
import com.gudu.xsd.modules.vision.application.VisionJobAppService;
import com.gudu.xsd.modules.vision.application.dto.ApplyItem;
import com.gudu.xsd.modules.vision.application.dto.VisionJobView;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 拍照识别（showcase-ai-community §4）：建任务（异步）→ 轮询结果 → 确认放进余量。 */
@RestController
@RequestMapping("/vision/jobs")
@RequiredArgsConstructor
@Tag(name = "拍照识别")
public class VisionController {

    private final VisionJobAppService appService;

    @Data
    public static class CreateReq {
        /** 预留：上传服务暂无文件 id，传 imageUrl。 */
        private Long fileId;
        private String imageUrl;
        private String mode;
    }

    @Data
    public static class ApplyReq {
        private List<ApplyItem> items;
    }

    @PostMapping
    public R<Map<String, Object>> create(@RequestBody CreateReq req) {
        if (req.getImageUrl() == null && req.getFileId() != null) {
            throw new BizException("暂不支持 fileId，请传上传接口返回的 imageUrl");
        }
        VisionJobView v = appService.create(StpUtil.getLoginIdAsLong(), req.getMode(), req.getImageUrl());
        return R.ok(Map.of("jobId", v.jobId(), "status", v.status()));
    }

    @GetMapping("/{id}")
    public R<VisionJobView> get(@PathVariable Long id) {
        return R.ok(appService.get(StpUtil.getLoginIdAsLong(), id));
    }

    @PostMapping("/{id}/apply")
    public R<Map<String, Integer>> apply(@PathVariable Long id, @RequestBody ApplyReq req) {
        return R.ok(Map.of("added", appService.apply(StpUtil.getLoginIdAsLong(), id, req.getItems())));
    }
}
