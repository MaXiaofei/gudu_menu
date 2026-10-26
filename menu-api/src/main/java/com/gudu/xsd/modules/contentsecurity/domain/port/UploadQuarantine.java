package com.gudu.xsd.modules.contentsecurity.domain.port;

/**
 * 违规图片隔离：把本站上传文件（原图 + 同名缩略图）移出对外服务的上传目录，
 * 所有引用该地址的菜谱 / 食记 / 头像 / 帖子随即加载不到，达到「引用处不可见」；文件保留在隔离目录供申诉复核。
 */
public interface UploadQuarantine {

    /** @return 是否移走了至少一个文件 */
    boolean quarantine(String uploadedUrl);
}
