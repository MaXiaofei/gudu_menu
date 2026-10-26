package com.gudu.xsd.modules.contentsecurity.infrastructure;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LocalUploadQuarantineTest {

    @TempDir
    Path tmp;

    @Test
    void 原图与缩略图移入隔离目录_原地址换成同格式占位图() throws Exception {
        Path uploads = tmp.resolve("uploads");
        Files.createDirectories(uploads.resolve("original/20261026"));
        Files.createDirectories(uploads.resolve("thumbnail/20261026"));
        Files.writeString(uploads.resolve("original/20261026/a.jpg"), "x");
        Files.writeString(uploads.resolve("thumbnail/20261026/a.jpg"), "t");
        LocalUploadQuarantine q = new LocalUploadQuarantine(uploads.toString(), "");

        assertThat(q.quarantine("/gudu/uploads/original/20261026/a.jpg")).isTrue();
        Path qroot = tmp.resolve("uploads-quarantine");
        assertThat(qroot.resolve("original/20261026/a.jpg")).hasContent("x");
        assertThat(qroot.resolve("thumbnail/20261026/a.jpg")).hasContent("t");

        // 原地址：占位图（JPEG 可解码，600px），引用处不出现坏图
        for (String rel : new String[]{"original/20261026/a.jpg", "thumbnail/20261026/a.jpg"}) {
            byte[] bytes = Files.readAllBytes(uploads.resolve(rel));
            assertThat(bytes[0] & 0xFF).as("JPEG 魔数").isEqualTo(0xFF);
            assertThat(bytes[1] & 0xFF).isEqualTo(0xD8);
            BufferedImage img = ImageIO.read(uploads.resolve(rel).toFile());
            assertThat(img.getWidth()).isEqualTo(600);
        }

        // 外链 / 穿越 / 不存在：无操作
        assertThat(q.quarantine("https://evil.com/a.jpg")).isFalse();
        assertThat(q.quarantine("/gudu/uploads/../secret")).isFalse();
        assertThat(q.quarantine("/gudu/uploads/original/20261026/none.jpg")).isFalse();
    }

    @Test
    void 占位图按扩展名编码_webp等不支持的写PNG() throws Exception {
        byte[] png = LocalUploadQuarantine.placeholder("original/x.png");
        assertThat(new String(png, 1, 3)).isEqualTo("PNG");
        byte[] webp = LocalUploadQuarantine.placeholder("original/x.webp");
        assertThat(new String(webp, 1, 3)).isEqualTo("PNG");
        byte[] gif = LocalUploadQuarantine.placeholder("original/x.gif");
        assertThat(new String(gif, 0, 3)).isEqualTo("GIF");
    }
}
