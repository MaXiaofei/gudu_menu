package com.gudu.xsd.modules.file;

import com.gudu.xsd.common.R;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.util.Map;

/**
 * 文件上传（MVP 本地存储；MinIO 接入留 V1）。
 */
@RestController
@RequestMapping("/file")
@Tag(name = "文件")
public class FileController {

    @Value("${gudu.upload-dir:uploads}")
    private String uploadDir;

    @PostMapping("/upload")
    public R<Map<String, String>> upload(@RequestParam("file") MultipartFile file) throws Exception {
        String original = (file.getOriginalFilename() == null) ? "file" : file.getOriginalFilename();
        String ext = original.contains(".") ? original.substring(original.lastIndexOf(".")) : "";
        String name = System.currentTimeMillis() + ext;
        File dest = new File(uploadDir, name).getAbsoluteFile();
        File parent = dest.getParentFile();
        if (!parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("无法创建上传目录: " + parent.getAbsolutePath());
        }
        Files.copy(file.getInputStream(), dest.toPath());
        return R.ok(Map.of("url", "/uploads/" + name, "name", original));
    }
}
