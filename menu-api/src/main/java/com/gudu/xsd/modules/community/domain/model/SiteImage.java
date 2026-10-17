package com.gudu.xsd.modules.community.domain.model;

import com.gudu.xsd.common.domain.DomainException;
import com.gudu.xsd.common.domain.UploadedImageUrl;

import java.util.regex.Pattern;

/**
 * 帖子图片地址（值对象）：只接受本站图片——FileController 上传的 /uploads 相对路径（复用 {@link UploadedImageUrl} 规则），
 * 或本站图片域名下的菜谱图（imxf.cloud / staging.imxf.cloud 的 /images、/uploads）。其余一律视为外链拒绝。
 */
public record SiteImage(String value) {

    static final int MAX_LENGTH = 255;
    private static final Pattern OWN_HOST = Pattern.compile(
            "^https://(staging\\.)?imxf\\.cloud(/gudu)?/(images|uploads)/[A-Za-z0-9_\\-./]+$");

    public SiteImage {
        if (!isValid(value)) throw new DomainException("图片必须是本站上传的图片");
    }

    public static boolean isValid(String value) {
        if (value == null || value.length() > MAX_LENGTH || value.contains("..")) return false;
        return UploadedImageUrl.isValid(value) || OWN_HOST.matcher(value).matches();
    }
}
