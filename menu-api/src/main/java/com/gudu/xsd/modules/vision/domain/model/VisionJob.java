package com.gudu.xsd.modules.vision.domain.model;

import com.gudu.xsd.common.domain.AggregateRoot;
import com.gudu.xsd.common.domain.DomainException;
import com.gudu.xsd.common.domain.UploadedImageUrl;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 拍照识别任务（聚合根）。生命周期：PENDING → DONE / FAILED；DONE 后可「放进余量」一次。
 * 原图保留 24 小时（到期清理图片、保留结构化结果），结果保留 7 天。
 */
public final class VisionJob extends AggregateRoot<Long> {

    public static final int IMAGE_TTL_HOURS = 24;
    public static final int RESULT_TTL_DAYS = 7;
    public static final int MAX_ERROR = 200;

    private Long id;
    private final Long memberId;
    private final VisionMode mode;
    private String imageUrl;
    private VisionJobStatus status;
    private String error;
    private List<RecognizedItem> items;
    private RawRecipe recipe;
    private final LocalDateTime createdAt;
    private LocalDateTime appliedAt;
    private boolean degraded;

    private VisionJob(Long id, Long memberId, VisionMode mode, String imageUrl, VisionJobStatus status, String error,
                      List<RecognizedItem> items, RawRecipe recipe, LocalDateTime createdAt, LocalDateTime appliedAt) {
        this.id = id;
        this.memberId = memberId;
        this.mode = mode;
        this.imageUrl = imageUrl;
        this.status = status;
        this.error = error;
        this.items = items == null ? List.of() : List.copyOf(items);
        this.recipe = recipe;
        this.createdAt = createdAt;
        this.appliedAt = appliedAt;
    }

    /** 新建识别任务：只接受本站上传的图片地址。 */
    public static VisionJob create(Long memberId, VisionMode mode, String imageUrl, LocalDateTime now) {
        if (memberId == null) throw new DomainException("请先登录");
        if (mode == null) throw new DomainException("识别模式不能为空");
        if (imageUrl == null || imageUrl.isBlank()) throw new DomainException("请先上传照片");
        new UploadedImageUrl(imageUrl.trim());
        return new VisionJob(null, memberId, mode, imageUrl.trim(), VisionJobStatus.PENDING, null,
                List.of(), null, now, null);
    }

    public static VisionJob rehydrate(Long id, Long memberId, VisionMode mode, String imageUrl, VisionJobStatus status,
                                      String error, List<RecognizedItem> items, RawRecipe recipe,
                                      LocalDateTime createdAt, LocalDateTime appliedAt) {
        return rehydrate(id, memberId, mode, imageUrl, status, error, items, recipe, createdAt, appliedAt, false);
    }

    public static VisionJob rehydrate(Long id, Long memberId, VisionMode mode, String imageUrl, VisionJobStatus status,
                                      String error, List<RecognizedItem> items, RawRecipe recipe,
                                      LocalDateTime createdAt, LocalDateTime appliedAt, boolean degraded) {
        VisionJob job = new VisionJob(id, memberId, mode, imageUrl, status, error, items, recipe, createdAt, appliedAt);
        job.degraded = degraded;
        return job;
    }

    /** 识别完成。 */
    public void complete(List<RecognizedItem> recognized, RawRecipe recognizedRecipe) {
        complete(recognized, recognizedRecipe, false);
    }

    /** 识别完成；degraded = 结果来自降级（后备模型或 Mock 示例），客户端应提示用户核对。 */
    public void complete(List<RecognizedItem> recognized, RawRecipe recognizedRecipe, boolean degradedResult) {
        requirePending();
        this.degraded = degradedResult;
        this.items = recognized == null ? List.of() : List.copyOf(recognized);
        this.recipe = mode == VisionMode.RECIPE ? recognizedRecipe : null;
        this.status = VisionJobStatus.DONE;
    }

    /** 识别失败（模型超时/返回不可解析等）。 */
    public void fail(String reason) {
        requirePending();
        String r = reason == null || reason.isBlank() ? "识别失败，请重拍一张" : reason.trim();
        this.error = r.length() > MAX_ERROR ? r.substring(0, MAX_ERROR) : r;
        this.status = VisionJobStatus.FAILED;
    }

    /** 放进余量：仅识别完成且未放过的任务可执行一次。 */
    public void markApplied(LocalDateTime now) {
        if (status != VisionJobStatus.DONE) throw new DomainException("识别还没完成");
        if (appliedAt != null) throw new DomainException("这次识别已经放进余量了");
        this.appliedAt = now;
    }

    /** 原图到期清理：只清地址，结构化结果保留。 */
    public void clearImage() {
        this.imageUrl = null;
    }

    public boolean ownedBy(Long member) {
        return memberId != null && memberId.equals(member);
    }

    public LocalDateTime imageExpiresAt() {
        return createdAt == null ? null : createdAt.plusHours(IMAGE_TTL_HOURS);
    }

    public LocalDateTime resultExpiresAt() {
        return createdAt == null ? null : createdAt.plusDays(RESULT_TTL_DAYS);
    }

    public void identify(Long newId) {
        if (this.id != null && !this.id.equals(newId)) throw new DomainException("聚合 id 不可变更");
        this.id = newId;
    }

    private void requirePending() {
        if (status != VisionJobStatus.PENDING) throw new DomainException("识别任务已结束");
    }

    @Override
    public Long id() {
        return id;
    }

    public Long memberId() {
        return memberId;
    }

    public VisionMode mode() {
        return mode;
    }

    public String imageUrl() {
        return imageUrl;
    }

    public VisionJobStatus status() {
        return status;
    }

    public String error() {
        return error;
    }

    public List<RecognizedItem> items() {
        return items;
    }

    public RawRecipe recipe() {
        return recipe;
    }

    public LocalDateTime createdAt() {
        return createdAt;
    }

    public boolean degraded() {
        return degraded;
    }

    public LocalDateTime appliedAt() {
        return appliedAt;
    }
}
