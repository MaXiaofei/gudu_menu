package com.gudu.xsd.modules.file;

import net.coobird.thumbnailator.Thumbnails;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

/**
 * 上传图片去元数据（A.3）：按 EXIF Orientation 把像素转正后重新编码，输出不带 EXIF/GPS/XMP 等任何元数据。
 *
 * <p>重新编码由 Thumbnailator（ImageIO）完成：读取时 useExifOrientation 先旋转，写出时 ImageIO 只写像素
 * （JPEG 仅 JFIF 头），原文件的 APP1(Exif/XMP)、PNG 文本块等全部丢弃。GIF 本身无 EXIF，为保留动图原样存放。
 */
final class ImageSanitizer {

    /** 扩展名 → ImageIO 输出格式；不在表里的（gif）原样存放。 */
    private static final Map<String, String> REENCODE = Map.of(
            ".jpg", "jpg",
            ".jpeg", "jpg",
            ".png", "png",
            ".bmp", "bmp");

    private static final double JPEG_QUALITY = 0.92;

    private ImageSanitizer() {
    }

    /** 该扩展名是否需要重新编码去元数据。 */
    static boolean reencodes(String ext) {
        return REENCODE.containsKey(ext);
    }

    /**
     * 读入图片 → 按 EXIF 方向转正 → 去元数据写出。
     *
     * @throws IOException 图片无法解码（损坏 / 格式与扩展名不符且无法识别）
     */
    static void sanitize(InputStream in, String ext, OutputStream out) throws IOException {
        String format = REENCODE.get(ext);
        if (format == null) {
            in.transferTo(out);
            return;
        }
        Thumbnails.Builder<? extends InputStream> b = Thumbnails.of(in)
                .scale(1.0)
                .useExifOrientation(true)
                .outputFormat(format);
        if ("jpg".equals(format)) b.outputQuality(JPEG_QUALITY);
        b.toOutputStream(out);
    }
}
