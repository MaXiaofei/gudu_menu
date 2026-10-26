package com.gudu.xsd.modules.file;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** A.3：上传图片先按 EXIF 方向转正，再去除 EXIF/GPS 元数据。 */
class ImageSanitizerTest {

    @Test
    void JPEG_按方向转正且去除EXIF与GPS() throws Exception {
        byte[] src = ExifJpeg.withOrientation6AndGps();
        assertThat(ExifJpeg.hasExif(src)).as("夹具本身带 EXIF").isTrue();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageSanitizer.sanitize(new ByteArrayInputStream(src), ".jpg", out);
        byte[] cleaned = out.toByteArray();

        assertThat(ExifJpeg.hasExif(cleaned)).as("EXIF（含 GPS）已去除").isFalse();
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(cleaned));
        assertThat(img.getWidth()).as("Orientation=6 已转正").isEqualTo(ExifJpeg.HEIGHT);
        assertThat(img.getHeight()).isEqualTo(ExifJpeg.WIDTH);
    }

    @Test
    void 无法解码的图片_抛异常() {
        assertThatThrownBy(() -> ImageSanitizer.sanitize(new ByteArrayInputStream("not an image".getBytes()), ".jpg",
                new ByteArrayOutputStream())).isInstanceOf(Exception.class);
    }

    @Test
    void 只重编码可去元数据的格式() {
        assertThat(ImageSanitizer.reencodes(".jpg")).isTrue();
        assertThat(ImageSanitizer.reencodes(".png")).isTrue();
        assertThat(ImageSanitizer.reencodes(".gif")).isFalse();
    }

    @Test
    void 文件名为随机UUID_不含时间戳() {
        String a = FileController.randomName(".jpg");
        String b = FileController.randomName(".jpg");
        assertThat(a).matches("^[0-9a-f]{32}\\.jpg$").isNotEqualTo(b);
    }
}
