package com.gudu.xsd.modules.contentsecurity.domain.port;

/**
 * 违规图片隔离：把本站上传文件（原图 + 同名缩略图）移出对外服务的上传目录（保留供申诉复核），
 * 原地址换成「图片未通过审核」占位图——所有引用该地址的菜谱 / 食记 / 评价等不再显示原图，也不会出现坏图。
 * 头像与厨房圈帖子另由 MediaBlockedEvent 订阅方清空 / 隐藏。
 */
public interface UploadQuarantine {

    /** @return 是否移走了至少一个文件 */
    boolean quarantine(String uploadedUrl);

    /**
     * 人工复核为误判（F.3）：把隔离的原图 + 同名缩略图移回原地址（覆盖占位图）。
     *
     * @return 是否还原了至少一个文件（隔离目录里没有 → false）
     */
    boolean release(String uploadedUrl);

    /** 读取隔离中的原图（运营复核查看，原地址已是占位图）；不在隔离目录 → empty。 */
    java.util.Optional<byte[]> readQuarantined(String uploadedUrl);
}
