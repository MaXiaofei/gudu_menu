package com.gudu.xsd.modules.contentsecurity.infrastructure;

import com.gudu.xsd.common.domain.UploadedImageUrl;
import com.gudu.xsd.modules.contentsecurity.domain.port.UploadQuarantine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

/**
 * 本地上传目录（gudu.upload-dir）的违规图片隔离：
 * <ol>
 *   <li>原图 + 同名缩略图移到隔离目录（gudu.content-security.quarantine-dir，默认「上传目录-quarantine」，
 *       不在 /uploads 对外服务范围内），留作申诉复核；</li>
 *   <li>原地址写入「图片未通过审核」占位图（按扩展名转成对应格式）。</li>
 * </ol>
 * 选择「原地址换占位图」而不是在各接口出参里过滤 url：一次覆盖菜谱封面 / 步骤图 / 食记 / 评价 / 反馈 / 识别 / 帖子等
 * 所有引用处与三端（含 Flutter、后台、历史缓存数据），不需要逐个接口改造，引用处也不会出现坏图或图片数组错位。
 */
@Slf4j
@Component
public class LocalUploadQuarantine implements UploadQuarantine {

    static final String PLACEHOLDER = "/content-security/blocked.png";

    private final Path root;
    private final Path quarantineRoot;

    public LocalUploadQuarantine(@Value("${gudu.upload-dir:uploads}") String uploadDir,
                                 @Value("${gudu.content-security.quarantine-dir:}") String quarantineDir) {
        this.root = Path.of(uploadDir).toAbsolutePath().normalize();
        this.quarantineRoot = (quarantineDir == null || quarantineDir.isBlank()
                ? Path.of(root + "-quarantine") : Path.of(quarantineDir)).toAbsolutePath().normalize();
    }

    @Override
    public boolean quarantine(String uploadedUrl) {
        if (!UploadedImageUrl.isValid(uploadedUrl)) return false;
        String rel = uploadedUrl.replaceFirst("^(/gudu)?/uploads/", "");
        boolean moved = move(rel);
        if (rel.startsWith("original/")) moved |= move("thumbnail/" + rel.substring("original/".length()));
        return moved;
    }

    @Override
    public boolean release(String uploadedUrl) {
        if (!UploadedImageUrl.isValid(uploadedUrl)) return false;
        String rel = uploadedUrl.replaceFirst("^(/gudu)?/uploads/", "");
        boolean restored = moveBack(rel);
        if (rel.startsWith("original/")) restored |= moveBack("thumbnail/" + rel.substring("original/".length()));
        return restored;
    }

    @Override
    public java.util.Optional<byte[]> readQuarantined(String uploadedUrl) {
        if (!UploadedImageUrl.isValid(uploadedUrl)) return java.util.Optional.empty();
        String rel = uploadedUrl.replaceFirst("^(/gudu)?/uploads/", "");
        Path src = quarantineRoot.resolve(rel).normalize();
        if (!src.startsWith(quarantineRoot) || !Files.isRegularFile(src)) return java.util.Optional.empty();
        try {
            return java.util.Optional.of(Files.readAllBytes(src));
        } catch (IOException e) {
            log.warn("读取隔离图片失败 {}: {}", src, e.getMessage());
            return java.util.Optional.empty();
        }
    }

    private boolean moveBack(String rel) {
        Path src = quarantineRoot.resolve(rel).normalize();
        Path dst = root.resolve(rel).normalize();
        if (!src.startsWith(quarantineRoot) || !dst.startsWith(root) || !Files.isRegularFile(src)) return false;
        try {
            Files.createDirectories(dst.getParent());
            Files.move(src, dst, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            log.error("还原隔离图片失败 {}: {}", src, e.getMessage());
            return false;
        }
    }

    private boolean move(String rel) {
        Path src = root.resolve(rel).normalize();
        Path dst = quarantineRoot.resolve(rel).normalize();
        if (!src.startsWith(root) || !dst.startsWith(quarantineRoot) || !Files.isRegularFile(src)) return false;
        try {
            Files.createDirectories(dst.getParent());
            Files.move(src, dst, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("隔离违规图片失败 {}: {}", src, e.getMessage());
            return false;
        }
        try {
            Files.write(src, placeholder(rel));
        } catch (IOException e) {
            // 占位图写失败：原地址 404，仍不可见
            log.error("写入占位图失败 {}: {}", src, e.getMessage());
        }
        return true;
    }

    /** 占位图按目标扩展名编码（jpg/png/gif/bmp；webp 等 ImageIO 不支持的写 PNG，浏览器与小程序按内容识别）。 */
    static byte[] placeholder(String rel) throws IOException {
        BufferedImage img;
        try (InputStream in = LocalUploadQuarantine.class.getResourceAsStream(PLACEHOLDER)) {
            if (in == null) throw new IOException("缺少占位图资源 " + PLACEHOLDER);
            img = ImageIO.read(in);
        }
        String name = rel.toLowerCase(Locale.ROOT);
        String format = name.endsWith(".jpg") || name.endsWith(".jpeg") ? "jpg"
                : name.endsWith(".gif") ? "gif" : name.endsWith(".bmp") ? "bmp" : "png";
        if (!"png".equals(format)) {
            BufferedImage rgb = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
            rgb.getGraphics().drawImage(img, 0, 0, null);
            img = rgb;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(img, format, out)) ImageIO.write(img, "png", out);
        return out.toByteArray();
    }
}
