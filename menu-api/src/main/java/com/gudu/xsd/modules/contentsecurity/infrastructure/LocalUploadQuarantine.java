package com.gudu.xsd.modules.contentsecurity.infrastructure;

import com.gudu.xsd.common.domain.UploadedImageUrl;
import com.gudu.xsd.modules.contentsecurity.domain.port.UploadQuarantine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 本地上传目录（gudu.upload-dir）的违规图片隔离：原图 + 同名缩略图移到隔离目录
 * （gudu.content-security.quarantine-dir，默认「上传目录-quarantine」，不在 /uploads 对外服务范围内）。
 */
@Slf4j
@Component
public class LocalUploadQuarantine implements UploadQuarantine {

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

    private boolean move(String rel) {
        Path src = root.resolve(rel).normalize();
        Path dst = quarantineRoot.resolve(rel).normalize();
        if (!src.startsWith(root) || !dst.startsWith(quarantineRoot) || !Files.isRegularFile(src)) return false;
        try {
            Files.createDirectories(dst.getParent());
            Files.move(src, dst, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            log.error("隔离违规图片失败 {}: {}", src, e.getMessage());
            return false;
        }
    }
}
