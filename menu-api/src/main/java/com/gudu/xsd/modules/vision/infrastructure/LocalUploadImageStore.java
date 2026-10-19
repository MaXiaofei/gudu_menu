package com.gudu.xsd.modules.vision.infrastructure;

import com.gudu.xsd.common.domain.UploadedImageUrl;
import com.gudu.xsd.modules.vision.domain.port.ImageStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/** 本地上传目录（gudu.upload-dir）里的识别原图删除：原图 + 同名缩略图。 */
@Slf4j
@Component
public class LocalUploadImageStore implements ImageStore {

    private final Path root;

    public LocalUploadImageStore(@Value("${gudu.upload-dir:uploads}") String uploadDir) {
        this.root = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    @Override
    public void delete(String uploadedUrl) {
        if (!UploadedImageUrl.isValid(uploadedUrl)) return;
        String rel = uploadedUrl.replaceFirst("^(/gudu)?/uploads/", "");
        deleteQuietly(rel);
        if (rel.startsWith("original/")) deleteQuietly("thumbnail/" + rel.substring("original/".length()));
    }

    @Override
    public Optional<byte[]> read(String uploadedUrl) {
        if (!UploadedImageUrl.isValid(uploadedUrl)) return Optional.empty();
        Path p = root.resolve(uploadedUrl.replaceFirst("^(/gudu)?/uploads/", "")).normalize();
        if (!p.startsWith(root) || !Files.isRegularFile(p)) return Optional.empty();
        try {
            return Optional.of(Files.readAllBytes(p));
        } catch (IOException e) {
            log.warn("读取识别原图失败 {}: {}", p, e.getMessage());
            return Optional.empty();
        }
    }

    private void deleteQuietly(String rel) {
        Path p = root.resolve(rel).normalize();
        if (!p.startsWith(root)) return;
        try {
            Files.deleteIfExists(p);
        } catch (IOException e) {
            log.warn("删除识别原图失败 {}: {}", p, e.getMessage());
        }
    }
}
