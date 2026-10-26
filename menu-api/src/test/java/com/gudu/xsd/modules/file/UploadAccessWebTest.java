package com.gudu.xsd.modules.file;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gudu.xsd.common.upload.UploadUrlSigner;
import com.gudu.xsd.modules.contentsecurity.application.ContentSecurityAppService;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.defaults.DefaultSqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import javax.imageio.ImageIO;
import javax.sql.DataSource;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A.3：/uploads/** 须带短时签名（存量时间戳文件同样）；上传返回带签名的 UUID 地址，落盘文件已去 EXIF 并转正。
 */
@WebMvcTest(
        value = FileController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = com.gudu.xsd.config.SaTokenConfig.class),
        properties = {
                "gudu.upload-sign.enabled=true",
                "gudu.upload-sign.secret=webmvc-test-upload-secret-0123"
        })
@Import(UploadAccessWebTest.TestSqlConfig.class)
class UploadAccessWebTest {

    static final Path uploadDir;

    static {
        try {
            uploadDir = Files.createTempDirectory("gudu-upload-access");
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @TestConfiguration
    static class TestSqlConfig {
        @Bean
        DataSource dataSource() {
            return org.mockito.Mockito.mock(DataSource.class);
        }

        @Bean
        SqlSessionFactory sqlSessionFactory(DataSource ds) {
            return new DefaultSqlSessionFactory(new Configuration(
                    new Environment("test", new JdbcTransactionFactory(), ds)));
        }
    }

    @DynamicPropertySource
    static void dirs(DynamicPropertyRegistry r) {
        r.add("gudu.upload-dir", uploadDir::toString);
    }

    @BeforeAll
    static void legacyFiles() throws Exception {
        // 存量：时间戳文件名的原图与缩略图
        Path orig = Files.createDirectories(uploadDir.resolve("original/20260101"));
        Path thumb = Files.createDirectories(uploadDir.resolve("thumbnail/20260101"));
        Files.write(orig.resolve("1767225600123.jpg"), new byte[]{1, 2, 3});
        Files.write(thumb.resolve("1767225600123.jpg"), new byte[]{1, 2});
    }

    @Autowired MockMvc mvc;
    @Autowired UploadUrlSigner signer;
    @MockBean ContentSecurityAppService contentSecurity;

    static final String LEGACY = "/gudu/uploads/original/20260101/1767225600123.jpg";

    /** 去掉 /gudu 前缀（MockMvc 不带 context-path）。 */
    static String path(String url) {
        return url.replaceFirst("^/gudu", "");
    }

    @Test
    void 未签名访问_存量时间戳文件也403() throws Exception {
        mvc.perform(get(path(LEGACY))).andExpect(status().isForbidden());
        mvc.perform(get("/uploads/thumbnail/20260101/1767225600123.jpg")).andExpect(status().isForbidden());
    }

    @Test
    void 带签名访问_原图与缩略图都可读() throws Exception {
        String signed = signer.sign(LEGACY);
        mvc.perform(get(path(signed))).andExpect(status().isOk());
        mvc.perform(get(path(signed).replace("/original/", "/thumbnail/"))).andExpect(status().isOk());
    }

    @Test
    void 篡改签名或换文件_403() throws Exception {
        String signed = signer.sign(LEGACY);
        mvc.perform(get(path(signed).replace("1767225600123", "1767225600124"))).andExpect(status().isForbidden());
        mvc.perform(get(path(signed) + "x")).andExpect(status().isForbidden());
        mvc.perform(get(path(signed).replaceFirst("e=\\d+", "e=4102444800"))).andExpect(status().isForbidden());
    }

    @Test
    void 上传_返回带签名UUID地址_落盘去EXIF已转正() throws Exception {
        byte[] jpeg = ExifJpeg.withOrientation6AndGps();
        MockMultipartFile file = new MockMultipartFile("file", "IMG_0001.jpeg", "image/jpeg", jpeg);
        String body;
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::isLogin).thenReturn(false);
            body = mvc.perform(multipart("/file/upload").file(file))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        }
        JsonNode data = new ObjectMapper().readTree(body).get("data");
        String url = data.get("url").asText();
        String thumb = data.get("thumbnailUrl").asText();
        assertThat(url).matches("^/gudu/uploads/original/\\d{8}/[0-9a-f]{32}\\.jpg\\?e=\\d+&s=[A-Za-z0-9_\\-]+$");
        assertThat(thumb).contains("/uploads/thumbnail/").contains("?e=");

        // 落盘：无 EXIF、已转正（40x20 + Orientation=6 → 20x40）
        String rel = UploadUrlSigner.strip(url).replaceFirst("^/gudu/uploads/", "");
        byte[] stored = Files.readAllBytes(uploadDir.resolve(rel));
        assertThat(ExifJpeg.hasExif(stored)).isFalse();
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(stored));
        assertThat(img.getWidth()).isEqualTo(ExifJpeg.HEIGHT);
        assertThat(img.getHeight()).isEqualTo(ExifJpeg.WIDTH);

        // 返回的地址可直接访问；内容安全提交的是不带签名的规范地址
        mvc.perform(get(path(url))).andExpect(status().isOk());
        mvc.perform(get(path(thumb))).andExpect(status().isOk());
        org.mockito.Mockito.verify(contentSecurity).submitImageLater(
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq(UploadUrlSigner.strip(url)),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void 上传_无法解码的图片拒绝且不落盘() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "x.jpg", "image/jpeg", "not an image".getBytes());
        String body;
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::isLogin).thenReturn(false);
            body = mvc.perform(multipart("/file/upload").file(file))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        }
        assertThat(new ObjectMapper().readTree(body).get("code").asInt()).isNotZero();
    }
}
