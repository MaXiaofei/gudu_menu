package com.gudu.xsd.common.upload;

/** 客户端提交的本站上传地址缺少有效签名（不是服务端下发的地址，或已过期太久）。 */
public class UploadUrlRejectedException extends RuntimeException {

    public static final String MESSAGE = "图片地址已失效，请刷新页面或重新上传后再试";

    public UploadUrlRejectedException() {
        super(MESSAGE);
    }
}
