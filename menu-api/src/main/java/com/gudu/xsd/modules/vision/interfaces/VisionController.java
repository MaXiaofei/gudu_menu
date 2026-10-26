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
    private final com.gudu.xsd.modules.contentsecurity.application.ContentSecurityAppService contentSecurity;

    /** AI 生成文本输出检测：识别项名称 / 菜谱标题、食材、步骤违规则替换为安全提示。 */
    private VisionJobView safe(VisionJobView v) {
        if (v == null || (v.items() == null && v.recipe() == null)) return v;
        String hidden = ContentSecurityAppService.AI_TEXT_REPLACED;
        // 识别项名称违规：整项剔除（避免把安全提示当食材名放进余量）；备选名违规：剔除该备选
        java.util.List<com.gudu.xsd.modules.vision.domain.model.RecognizedItem> items = v.items() == null ? null : v.items().stream()
                .filter(i -> !hidden.equals(contentSecurity.reviewAiText(i.name())))
                .map(i -> new com.gudu.xsd.modules.vision.domain.model.RecognizedItem(i.name(), i.rawName(), i.ingredientId(),
                        i.iconKey(), i.amount(), i.unit(), i.confidence(), i.level(),
                        i.alternatives().stream().filter(a -> !hidden.equals(contentSecurity.reviewAiText(a))).toList(),
                        i.suggestedBestBefore())).toList();
        VisionJobView.RecipeView r = v.recipe() == null ? null : new VisionJobView.RecipeView(
                contentSecurity.reviewAiText(v.recipe().title()),
                v.recipe().ingredients() == null ? null : v.recipe().ingredients().stream()
                        .map(l -> new VisionJobView.Line(l.ingredientId(), contentSecurity.reviewAiText(l.name()), l.iconKey(), l.amount(), l.unit())).toList(),
                v.recipe().steps() == null ? null : v.recipe().steps().stream().map(contentSecurity::reviewAiText).toList());
        return new VisionJobView(v.jobId(), v.mode(), v.status(), v.error(), v.imageExpiresAt(), items, r, v.degraded());
    }

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
        return R.ok(safe(appService.get(StpUtil.getLoginIdAsLong(), id)));
    }

    @PostMapping("/{id}/apply")
    public R<Map<String, Integer>> apply(@PathVariable Long id, @RequestBody ApplyReq req) {
        return R.ok(Map.of("added", appService.apply(StpUtil.getLoginIdAsLong(), id, req.getItems())));
    }
}
