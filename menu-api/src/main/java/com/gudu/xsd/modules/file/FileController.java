package com.gudu.xsd.modules.file;

import cn.dev33.satoken.stp.StpUtil;
import com.gudu.xsd.common.BizException;
import com.gudu.xsd.common.R;
import com.gudu.xsd.modules.contentsecurity.application.ContentSecurityAppService;
import com.gudu.xsd.modules.contentsecurity.domain.model.ContentPurpose;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 文件上传：原图 + 缩略图（400px 宽）。
 *
 * 目录结构（uploadDir 由 gudu.upload-dir 配置，各环境独立）：
 * 按日期分目录（YYYYMMDD，上传时无当天目录自动创建，便于归档维护）：
 *   {uploadDir}/
 *     original/{YYYYMMDD}/   ← 原图（前端已压缩）
 *     thumbnail/{YYYYMMDD}/  ← 缩略图（400px 宽，保持比例）
 *
 * 文件名为随机 UUID（A.3：不可按时间枚举；存量时间戳文件不改名）；图片按 EXIF 方向转正后重新编码，去除 EXIF/GPS 等元数据。
 *
 * 响应格式：{ url（原图）, thumbnailUrl（缩略图）, name（原始文件名）}。
 * 入库存不带签名的规范地址；响应经 HTTP 序列化统一加短时签名 ?e=&s=（UploadUrlJacksonModule），
 * 访问 /uploads/** 须带签名（UploadAccessConfig）。
 *
 * 内容安全（A.6）：图片保存后异步提交微信 mediaCheckAsync（usage=avatar/post/… 决定场景），
 * 违规由消息推送回调隔离文件；上传本身不等待检测结果。
 */
@Slf4j
@RestController
@RequestMapping("/file")
@Tag(name = "文件")
public class FileController {

    private static final int THUMBNAIL_WIDTH = 400;

    /** 语音（咕嘟语音输入）：允许的扩展名与大小上限（≈60 秒）。 */
    static final List<String> AUDIO_EXTS = List.of(".mp3", ".aac", ".m4a", ".wav");
    static final long AUDIO_MAX_BYTES = 2L * 1024 * 1024;
    static final List<String> IMAGE_EXTS = List.of(".jpg", ".jpeg", ".png", ".gif", ".bmp");

    @Value("${gudu.upload-dir:uploads}")
    private String uploadDir;

    private final ContentSecurityAppService contentSecurity;

    public FileController(ContentSecurityAppService contentSecurity) {
        this.contentSecurity = contentSecurity;
    }

    @PostMapping("/upload")
    public R<Map<String, String>> upload(@RequestParam("file") MultipartFile file,
                                         @RequestParam(value = "usage", required = false) String usage) throws Exception {
        String original = (file.getOriginalFilename() == null) ? "file" : file.getOriginalFilename();
        String ext = original.contains(".") ? original.substring(original.lastIndexOf(".")).toLowerCase() : ".jpg";
        if (AUDIO_EXTS.contains(ext)) {
            return R.ok(saveAudio(file, original, ext));
        }
        // 安全收窄扩展名（webp：ImageIO 无法解码去元数据，按 jpg 处理，解码失败即拒绝）
        if (!IMAGE_EXTS.contains(ext)) {
            ext = ".jpg";
        }
        if (".jpeg".equals(ext)) ext = ".jpg";
        String baseName = randomName(ext);
        // 按日期分目录（YYYYMMDD）：没有当天目录就创建，后期按日期归档维护
        String day = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
        String origSub = "original/" + day;
        String thumbSub = "thumbnail/" + day;

        File originalDir = new File(uploadDir, origSub).getAbsoluteFile();
        File thumbnailDir = new File(uploadDir, thumbSub).getAbsoluteFile();
        ensureDir(originalDir);
        ensureDir(thumbnailDir);

        // 1. 存原图：按 EXIF 方向转正并去除元数据（GPS 等）后写盘，原始字节不落盘
        File originalFile = new File(originalDir, baseName);
        try (InputStream in = file.getInputStream(); OutputStream out = Files.newOutputStream(originalFile.toPath())) {
            ImageSanitizer.sanitize(in, ext, out);
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(originalFile.toPath());
            log.info("图片无法识别 name={}: {}", original, e.getMessage());
            throw new BizException("图片无法识别，请上传 JPG/PNG 图片");
        }

        // 2. 生成缩略图（400px 宽，等比缩放）
        File thumbnailFile = new File(thumbnailDir, baseName);
        Thumbnails.of(originalFile)
                .width(THUMBNAIL_WIDTH)
                .keepAspectRatio(true)
                .outputQuality(0.85)
                .toFile(thumbnailFile);

        // 带 context-path /gudu 前缀，前端 <img src> 直接可用
        String prefix = "/gudu/uploads/";
        String url = prefix + origSub + "/" + baseName;
        contentSecurity.submitImageLater(StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null, url,
                ContentPurpose.ofUploadUsage(usage));
        return R.ok(Map.of(
                "url", url,
                "thumbnailUrl", prefix + thumbSub + "/" + baseName,
                "name", original
        ));
    }

    /** 语音：≤ 2MB，存 audio/{YYYYMMDD}/，不生成缩略图；时长由客户端限制在 60 秒内。 */
    private Map<String, String> saveAudio(MultipartFile file, String original, String ext) throws Exception {
        if (file.getSize() > AUDIO_MAX_BYTES) {
            throw new BizException("语音不能超过 2MB（约 60 秒）");
        }
        String day = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
        String sub = "audio/" + day;
        File dir = new File(uploadDir, sub).getAbsoluteFile();
        ensureDir(dir);
        String baseName = randomName(ext);
        Files.copy(file.getInputStream(), new File(dir, baseName).toPath());
        return Map.of("url", "/gudu/uploads/" + sub + "/" + baseName, "name", original, "type", "audio");
    }

    /** 随机文件名（32 位十六进制 UUID）：不可预测、不可按时间枚举。 */
    static String randomName(String ext) {
        return UUID.randomUUID().toString().replace("-", "") + ext;
    }

    private void ensureDir(File dir) {
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("无法创建上传目录: " + dir.getAbsolutePath());
        }
    }
}
