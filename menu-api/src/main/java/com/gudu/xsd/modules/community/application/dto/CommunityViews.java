package com.gudu.xsd.modules.community.application.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 厨房圈接口出参（字段与 openspec/changes/showcase-ai-community/contracts.md §2 对齐）。 */
public final class CommunityViews {

    private CommunityViews() {
    }

    /** 游标分页。nextCursor 为空表示没有更多。 */
    public record CursorPage<T>(List<T> items, String nextCursor) {
    }

    public record UserLite(Long userId, String nickname, String avatarUrl) {
    }

    public record Coverage(int have, int total, List<String> missing) {
    }

    public record DishCard(Long id, String name, String coverUrl, Integer minutes, String difficulty,
                           Coverage coverage, List<String> tags) {
    }

    public record RecipeCard(Long id, String name, String coverUrl, Integer minutes, String difficulty,
                             Coverage coverage, List<String> tags, long copyCount) {
    }

    public record PostCard(Long postId, UserLite author, String title, String coverUrl, double coverRatio,
                           int imageCount, long likeCount, boolean liked, long copyCount, List<String> tags) {
    }

    public record TopicRef(Long topicId, String name) {
    }

    public record PostDetail(Long postId, UserLite author, String title, String coverUrl, double coverRatio,
                             int imageCount, long likeCount, boolean liked, long copyCount, List<String> tags,
                             String text, List<String> images, List<TopicRef> topics, RecipeCard recipe,
                             long commentCount, long collectCount, boolean collected, boolean following,
                             LocalDateTime createdAt, String city, String status,
                             /* 仅作者可见的处置原因，如「图片未通过审核」；无则 null */ String statusReason) {
    }

    public record PublishResult(Long postId, String status) {
    }

    public record ReactionResult(long likeCount, long collectCount, boolean liked, boolean collected) {
    }

    public record CopyResult(Long dishId) {
    }

    public record CopyItem(UserLite user, String imageUrl) {
    }

    public record Items<T>(List<T> items) {
    }

    public record CommentView(Long commentId, UserLite author, String text, boolean isAuthor, long likeCount,
                              LocalDateTime createdAt, List<CommentView> replies) {
    }

    public record FollowResult(boolean following) {
    }

    public record TopicView(Long topicId, String name, String intro, String rule, LocalDate startDate,
                            LocalDate endDate, Integer weekNo, long participantCount, long postCount,
                            List<String> coverUrls) {
    }

    public record DayProgress(LocalDate date, boolean done, String coverUrl) {
    }

    public record MyProgress(List<DayProgress> days, int doneCount) {
    }

    public record RankRow(UserLite user, int days, long likeCount, int rank) {
    }

    public record TopicDetail(Long topicId, String name, String intro, String rule, LocalDate startDate,
                              LocalDate endDate, Integer weekNo, long participantCount, long postCount,
                              List<String> coverUrls, MyProgress myProgress, List<RankRow> ranking, Integer myRank) {
    }

    public record Banner(TopicView topic, List<UserLite> followingUpdates) {
    }

    public record AiText(String text) {
    }

    public record ScoreView(String label, double score) {
    }

    public record ReportView(UserLite reporter, String reason, LocalDateTime createdAt) {
    }

    public record ModerationView(String action, String reason, String note, UserLite operator, LocalDateTime at) {
    }

    public record AppealView(String text, LocalDateTime createdAt) {
    }

    public record AuthorHistory(long posts, long hidden, long bans) {
    }

    public record QueueItem(Long postId, String coverUrl, String title, UserLite author, String risk,
                            List<String> reasons, long reportCount, String status, LocalDateTime createdAt,
                            List<ScoreView> aiScores, String aiSummary, List<ReportView> reports,
                            ModerationView moderation, AppealView appeal, AuthorHistory authorHistory) {
    }

    /** 后台统计（今日口径；pending* 为当前待处理存量）。 */
    public record AdminStats(long newPosts, long fromCookLog, double autoPassRate, long pending, long pendingHigh,
                             long pendingMedium, long reports, long autoHidden, long appealed,
                             Double avgHandleMinutes) {
    }

    /** 管理员视角帖子详情：含审核中/已隐藏帖子，带被拦截的站外图片与处置记录。 */
    public record AdminPostDetail(Long postId, UserLite author, String title, String text, List<String> images,
                                  List<String> blockedImages, double coverRatio, DishCard dish, List<TopicRef> topics,
                                  String visibility, String status, String risk, List<String> reasons,
                                  List<ScoreView> aiScores, String aiSummary, boolean reviewed, long reportCount,
                                  List<ReportView> reports, long likeCount, long collectCount, long commentCount,
                                  long copyCount, Long fromCookRecordId, String city, LocalDateTime createdAt,
                                  ModerationView moderation, AppealView appeal, AuthorHistory authorHistory,
                                  LocalDateTime authorBannedUntil, List<ModerationView> logs) {
    }
}
