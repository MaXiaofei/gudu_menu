package com.gudu.xsd.common.privacy;

/**
 * 账号注销时删除本人上传的图片文件（头像、反馈截图等；隐私政策 §七.6）。
 *
 * <p>实现删除原图 + 同名缩略图 + 违规隔离目录里的副本；文件不存在视为成功（幂等）；
 * 非本站上传地址（外链 / 非法路径）忽略；真实 IO 失败抛 {@link IllegalStateException}，让注销记录标失败、后台可重试。
 */
public interface UploadedFileEraser {

    /** @return 实际删除的文件数 */
    int erase(String uploadedUrl);
}
