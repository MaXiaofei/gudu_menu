package com.gudu.xsd.common.domain;


import java.util.regex.Pattern;

/**
 * 本站上传图片地址（值对象）：只接受 FileController 产出的相对路径 /gudu/uploads/… 或 /uploads/…，
 * 拒绝外链（防 SSRF/钓鱼图）与路径穿越。
 */
public record UploadedImageUrl(String value) {

    static final int MAX_LENGTH = 255;
    private static final Pattern PATTERN = Pattern.compile("^(/gudu)?/uploads/[A-Za-z0-9_\\-./]+$");

    public UploadedImageUrl {
        if (!isValid(value)) throw new DomainException("图片必须是本站上传地址");
    }

    public static boolean isValid(String value) {
        return value != null && value.length() <= MAX_LENGTH && !value.contains("..")
                && PATTERN.matcher(value).matches();
    }
}
