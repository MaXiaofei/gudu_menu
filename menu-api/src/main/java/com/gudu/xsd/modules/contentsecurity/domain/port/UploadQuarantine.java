package com.gudu.xsd.modules.contentsecurity.domain.port;

/**
 * 违规图片隔离：把本站上传文件（原图 + 同名缩略图）移出对外服务的上传目录（保留供申诉复核），
 * 原地址换成「图片未通过审核」占位图——所有引用该地址的菜谱 / 食记 / 评价等不再显示原图，也不会出现坏图。
 * 头像与厨房圈帖子另由 MediaBlockedEvent 订阅方清空 / 隐藏。
 */
public interface UploadQuarantine {

    /** @return 是否移走了至少一个文件 */
    boolean quarantine(String uploadedUrl);
}
