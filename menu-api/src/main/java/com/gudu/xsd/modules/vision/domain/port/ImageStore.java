package com.gudu.xsd.modules.vision.domain.port;

/** 上传图片存储端口：识别原图到期删除（原图 + 缩略图）。 */
public interface ImageStore {

    /** 删除本站上传图片；不存在或非本站地址时忽略。 */
    void delete(String uploadedUrl);
}
