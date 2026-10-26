package com.gudu.xsd.modules.file;

import com.gudu.xsd.common.domain.UploadedImageUrl;
import com.gudu.xsd.common.privacy.UploadedFileEraser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** 本地上传目录（gudu.upload-dir）及其违规隔离目录里的文件删除：原图 + 同名缩略图。 */
@Component
public class LocalUploadFileEraser implements UploadedFileEraser {

    private final Path root;
    private final Path quarantineRoot;

    public LocalUploadFileEraser(@Value("${gudu.upload-dir:uploads}") String uploadDir,
                                 @Value("${gudu.content-security.quarantine-dir:}") String quarantineDir) {
        this.root = Path.of(uploadDir).toAbsolutePath().normalize();
        this.quarantineRoot = (quarantineDir == null || quarantineDir.isBlank()
                ? Path.of(root + "-quarantine") : Path.of(quarantineDir)).toAbsolutePath().normalize();
    }

    @Override
    public int erase(String uploadedUrl) {
        if (!UploadedImageUrl.isValid(uploadedUrl)) return 0;
        String rel = uploadedUrl.replaceFirst("^(/gudu)?/uploads/", "");
        int n = delete(root, rel) + delete(quarantineRoot, rel);
        if (rel.startsWith("original/")) {
            String thumb = "thumbnail/" + rel.substring("original/".length());
            n += delete(root, thumb) + delete(quarantineRoot, thumb);
        }
        return n;
    }

    private static int delete(Path base, String rel) {
        Path p = base.resolve(rel).normalize();
        if (!p.startsWith(base)) return 0;
        try {
            return Files.deleteIfExists(p) ? 1 : 0;
        } catch (IOException e) {
            throw new IllegalStateException("删除文件失败：" + rel, e);
        }
    }
}
