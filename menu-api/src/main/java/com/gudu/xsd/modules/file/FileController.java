package com.gudu.xsd.modules.file;

import com.gudu.xsd.common.R;
import io.swagger.v3.oas.annotations.tags.Tag;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

/**
 * 文件上传：原图 + 缩略图（400px 宽）。
 *
 * 目录结构（uploadDir 由 gudu.upload-dir 配置，各环境独立）：
 * 按日期分目录（YYYYMMDD，上传时无当天目录自动创建，便于归档维护）：
 *   {uploadDir}/
 *     original/{YYYYMMDD}/   ← 原图（前端已压缩）
 *     thumbnail/{YYYYMMDD}/  ← 缩略图（400px 宽，保持比例）
 *
 * 响应格式：{ url（原图）, thumbnailUrl（缩略图）, name（原始文件名）}。
 * URL 直接入库，前端拿地址自行加载。
 */
@RestController
@RequestMapping("/file")
@Tag(name = "文件")
public class FileController {

    private static final int THUMBNAIL_WIDTH = 400;

    @Value("${gudu.upload-dir:uploads}")
    private String uploadDir;

    @PostMapping("/upload")
    public R<Map<String, String>> upload(@RequestParam("file") MultipartFile file) throws Exception {
        String original = (file.getOriginalFilename() == null) ? "file" : file.getOriginalFilename();
        String ext = original.contains(".") ? original.substring(original.lastIndexOf(".")).toLowerCase() : ".jpg";
        // 安全收窄扩展名
        if (!List.of(".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp").contains(ext)) {
            ext = ".jpg";
        }
        String baseName = System.currentTimeMillis() + ext;
        // 按日期分目录（YYYYMMDD）：没有当天目录就创建，后期按日期归档维护
        String day = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
        String origSub = "original/" + day;
        String thumbSub = "thumbnail/" + day;

        File originalDir = new File(uploadDir, origSub).getAbsoluteFile();
        File thumbnailDir = new File(uploadDir, thumbSub).getAbsoluteFile();
        ensureDir(originalDir);
        ensureDir(thumbnailDir);

        // 1. 存原图
        File originalFile = new File(originalDir, baseName);
        Files.copy(file.getInputStream(), originalFile.toPath());

        // 2. 生成缩略图（400px 宽，等比缩放）
        File thumbnailFile = new File(thumbnailDir, baseName);
        Thumbnails.of(originalFile)
                .width(THUMBNAIL_WIDTH)
                .keepAspectRatio(true)
                .outputQuality(0.85)
                .toFile(thumbnailFile);

        // 带 context-path /gudu 前缀，前端 <img src> 直接可用
        String prefix = "/gudu/uploads/";
        return R.ok(Map.of(
                "url", prefix + origSub + "/" + baseName,
                "thumbnailUrl", prefix + thumbSub + "/" + baseName,
                "name", original
        ));
    }

    private void ensureDir(File dir) {
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("无法创建上传目录: " + dir.getAbsolutePath());
        }
    }
}
