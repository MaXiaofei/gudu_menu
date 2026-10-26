package com.gudu.xsd.modules.file;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/** 测试夹具：生成带 EXIF（Orientation=6 + GPS 纬度）的 JPEG。 */
final class ExifJpeg {

    static final int WIDTH = 40;
    static final int HEIGHT = 20;

    private ExifJpeg() {
    }

    /** 40x20 的 JPEG，EXIF Orientation=6（需顺时针转 90°，转正后应为 20x40），并带 GPS IFD。 */
    static byte[] withOrientation6AndGps() throws IOException {
        BufferedImage img = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.ORANGE);
        g.fillRect(0, 0, WIDTH, HEIGHT);
        g.dispose();
        ByteArrayOutputStream plain = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", plain);
        byte[] jpeg = plain.toByteArray();

        ByteBuffer tiff = ByteBuffer.allocate(64);
        tiff.put(new byte[]{'M', 'M', 0, 0x2A}).putInt(8);
        // IFD0：Orientation、GPS 指针
        tiff.putShort((short) 2);
        tiff.putShort((short) 0x0112).putShort((short) 3).putInt(1).putShort((short) 6).putShort((short) 0);
        tiff.putShort((short) 0x8825).putShort((short) 4).putInt(1).putInt(38);
        tiff.putInt(0);
        // GPS IFD：GPSLatitudeRef = "N"
        tiff.putShort((short) 1);
        tiff.putShort((short) 0x0001).putShort((short) 2).putInt(2).put(new byte[]{'N', 0, 0, 0});
        tiff.putInt(0);
        byte[] tiffBytes = new byte[tiff.position()];
        tiff.flip();
        tiff.get(tiffBytes);

        byte[] exifHeader = "Exif\0\0".getBytes(StandardCharsets.US_ASCII);
        int len = 2 + exifHeader.length + tiffBytes.length;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(jpeg, 0, 2); // SOI
        out.write(0xFF);
        out.write(0xE1);
        out.write((len >> 8) & 0xFF);
        out.write(len & 0xFF);
        out.write(exifHeader);
        out.write(tiffBytes);
        out.write(jpeg, 2, jpeg.length - 2);
        return out.toByteArray();
    }

    /** 字节里是否含 EXIF APP1 段标识。 */
    static boolean hasExif(byte[] bytes) {
        return indexOf(bytes, "Exif\0\0".getBytes(StandardCharsets.US_ASCII)) >= 0;
    }

    static int indexOf(byte[] hay, byte[] needle) {
        outer:
        for (int i = 0; i <= hay.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (hay[i + j] != needle[j]) continue outer;
            }
            return i;
        }
        return -1;
    }
}
