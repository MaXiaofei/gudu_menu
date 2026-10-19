package com.gudu.xsd.modules.community.interfaces;

import cn.dev33.satoken.stp.StpUtil;
import com.gudu.xsd.common.R;
import com.gudu.xsd.modules.community.application.CommunityFeedAppService;
import com.gudu.xsd.modules.community.application.CommunityPostAppService;
import com.gudu.xsd.modules.community.application.CommunityPostAppService.PublishCommand;
import com.gudu.xsd.modules.community.application.dto.CommunityViews.AiText;
import com.gudu.xsd.modules.community.application.dto.CommunityViews.Banner;
import com.gudu.xsd.modules.community.application.dto.CommunityViews.CommentView;
import com.gudu.xsd.modules.community.application.dto.CommunityViews.CopyItem;
import com.gudu.xsd.modules.community.application.dto.CommunityViews.CopyResult;
import com.gudu.xsd.modules.community.application.dto.CommunityViews.CursorPage;
import com.gudu.xsd.modules.community.application.dto.CommunityViews.FollowResult;
import com.gudu.xsd.modules.community.application.dto.CommunityViews.Items;
import com.gudu.xsd.modules.community.application.dto.CommunityViews.PostCard;
import com.gudu.xsd.modules.community.application.dto.CommunityViews.PostDetail;
import com.gudu.xsd.modules.community.application.dto.CommunityViews.PublishResult;
import com.gudu.xsd.modules.community.application.dto.CommunityViews.ReactionResult;
import com.gudu.xsd.modules.community.application.dto.CommunityViews.TopicDetail;
import com.gudu.xsd.modules.community.application.dto.CommunityViews.TopicView;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 厨房圈（契约：openspec/changes/showcase-ai-community/contracts.md §2）。 */
@RestController
@RequestMapping("/community")
@RequiredArgsConstructor
@Tag(name = "厨房圈")
public class CommunityController {

    private final CommunityPostAppService postAppService;
    private final CommunityFeedAppService feedAppService;

    @Data
    public static class PublishReq {
        private String title;
        private String text;
        private List<String> imageUrls;
        private Long dishId;
        private List<Long> topicIds;
        private String visibility;
        private Long fromCookRecordId;
        /** 食记 id（/food-log 返回的 foodLogId）；与 fromCookRecordId 二选一。 */
        private Long fromFoodLogId;
        /** 可选：封面宽/高，瀑布流排版用。 */
        private Double coverRatio;
        private String city;
    }

    @Data
    public static class ReactionReq {
        private String type;
        private Boolean on;
    }

    @Data
    public static class CommentReq {
        private String text;
        private Long parentId;
    }

    @Data
    public static class FollowReq {
        private Boolean on;
    }

    @Data
    public static class AiTextReq {
        private Long dishId;
        private Long cookRecordId;
        private List<String> imageUrls;
        private String style;
    }

    @Data
    public static class AppealReq {
        private String text;
    }

    @Data
    public static class ReportReq {
        private Long postId;
        private String reason;
    }

    @GetMapping("/feed")
    public R<CursorPage<PostCard>> feed(@RequestParam(required = false) String tab,
                                        @RequestParam(required = false) Long topicId,
                                        @RequestParam(required = false) String cursor,
                                        @RequestParam(required = false) Integer size) {
        return R.ok(feedAppService.feed(viewer(), tab, topicId, cursor, size));
    }

    @GetMapping("/banner")
    public R<Banner> banner() {
        return R.ok(feedAppService.banner(viewer()));
    }

    @PostMapping("/posts")
    public R<PublishResult> publish(@RequestBody PublishReq req) {
        return R.ok(postAppService.publish(viewer(), new PublishCommand(req.getTitle(), req.getText(), req.getImageUrls(),
                req.getDishId(), req.getTopicIds(), req.getVisibility(), req.getFromCookRecordId(),
                req.getFromFoodLogId(), req.getCoverRatio(), req.getCity())));
    }

    @GetMapping("/posts/{id}")
    public R<PostDetail> detail(@PathVariable Long id) {
        return R.ok(postAppService.detail(viewer(), id));
    }

    @DeleteMapping("/posts/{id}")
    public R<Map<String, Object>> delete(@PathVariable Long id) {
        postAppService.delete(viewer(), id);
        return R.ok(Map.of());
    }

    @PostMapping("/posts/{id}/reactions")
    public R<ReactionResult> react(@PathVariable Long id, @RequestBody ReactionReq req) {
        return R.ok(postAppService.react(viewer(), id, req.getType(), !Boolean.FALSE.equals(req.getOn())));
    }

    @PostMapping("/posts/{id}/copy-recipe")
    public R<CopyResult> copyRecipe(@PathVariable Long id) {
        return R.ok(postAppService.copyRecipe(viewer(), id));
    }

    @GetMapping("/posts/{id}/copies")
    public R<Items<CopyItem>> copies(@PathVariable Long id, @RequestParam(required = false) Integer size) {
        return R.ok(postAppService.copies(viewer(), id, size));
    }

    @GetMapping("/posts/{id}/comments")
    public R<CursorPage<CommentView>> comments(@PathVariable Long id, @RequestParam(required = false) String cursor,
                                               @RequestParam(required = false) Integer size) {
        return R.ok(postAppService.comments(viewer(), id, cursor, size));
    }

    @PostMapping("/posts/{id}/comments")
    public R<CommentView> comment(@PathVariable Long id, @RequestBody CommentReq req) {
        return R.ok(postAppService.comment(viewer(), id, req.getText(), req.getParentId()));
    }

    @PostMapping("/users/{id}/follow")
    public R<FollowResult> follow(@PathVariable Long id, @RequestBody FollowReq req) {
        return R.ok(feedAppService.follow(viewer(), id, !Boolean.FALSE.equals(req.getOn())));
    }

    @PostMapping("/posts/{id}/appeal")
    public R<Map<String, Object>> appeal(@PathVariable Long id, @RequestBody AppealReq req) {
        postAppService.appeal(viewer(), id, req.getText());
        return R.ok(Map.of());
    }

    @GetMapping("/topics")
    public R<List<TopicView>> topics(@RequestParam(required = false) String status,
                                     @RequestParam(required = false) Integer size) {
        return R.ok(feedAppService.topics(status, size));
    }

    @GetMapping("/topics/{id}")
    public R<TopicDetail> topic(@PathVariable Long id) {
        return R.ok(feedAppService.topicDetail(viewer(), id));
    }

    @PostMapping("/drafts/ai-text")
    public R<AiText> aiText(@RequestBody AiTextReq req) {
        return R.ok(postAppService.aiText(viewer(), req.getDishId(), req.getCookRecordId(), req.getStyle()));
    }

    @PostMapping("/reports")
    public R<Map<String, Object>> report(@RequestBody ReportReq req) {
        postAppService.report(viewer(), req.getPostId(), req.getReason());
        return R.ok(Map.of());
    }

    /** 当前登录 id（member.id）；未登录为 null（仅在 auth.login-check=false 的测试环境出现）。 */
    private static Long viewer() {
        return StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null;
    }
}
