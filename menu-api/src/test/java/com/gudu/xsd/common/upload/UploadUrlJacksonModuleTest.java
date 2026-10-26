package com.gudu.xsd.common.upload;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** HTTP 序列化层统一签名 / 反序列化统一验签剥签名：覆盖 DTO 字段、List、Map、多图字段、嵌套。 */
class UploadUrlJacksonModuleTest {

    static final String IMG = "/gudu/uploads/original/20261026/a.jpg";
    final UploadUrlSigner signer = new UploadUrlSigner("unit-test-upload-secret-01", Duration.ofHours(24),
            Duration.ofDays(7), true, Clock.fixed(Instant.parse("2026-10-26T03:00:00Z"), ZoneOffset.UTC));
    final ObjectMapper om = new ObjectMapper().registerModule(new UploadUrlJacksonModule(signer));

    public record Profile(Long id, String name, String avatarUrl, List<String> images, String imagesCsv,
                          Map<String, Object> extra) {
    }

    @Test
    void 出参_各种位置的上传地址都带签名_普通字符串不动() throws Exception {
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("cover", IMG);
        extra.put("nested", Map.of("thumb", "/gudu/uploads/thumbnail/20261026/a.jpg"));
        Profile p = new Profile(1L, "张爸爸", IMG, List.of(IMG), IMG + "," + IMG, extra);
        JsonNode n = om.readTree(om.writeValueAsString(Map.of("code", 0, "data", p)));
        JsonNode d = n.get("data");
        assertThat(d.get("name").asText()).isEqualTo("张爸爸");
        assertThat(d.get("avatarUrl").asText()).isEqualTo(signer.sign(IMG));
        assertThat(d.get("images").get(0).asText()).isEqualTo(signer.sign(IMG));
        assertThat(d.get("imagesCsv").asText()).isEqualTo(signer.sign(IMG) + "," + signer.sign(IMG));
        assertThat(d.get("extra").get("cover").asText()).isEqualTo(signer.sign(IMG));
        assertThat(d.get("extra").get("nested").get("thumb").asText()).contains("?e=");
    }

    @Test
    void 入参_带签名地址还原为规范地址() throws Exception {
        String body = om.writeValueAsString(new Profile(1L, "x", IMG, List.of(IMG), IMG + "," + IMG, Map.of("cover", IMG)));
        assertThat(body).contains("?e=");
        Profile back = om.readValue(body, Profile.class);
        assertThat(back.avatarUrl()).isEqualTo(IMG);
        assertThat(back.images()).containsExactly(IMG);
        assertThat(back.imagesCsv()).isEqualTo(IMG + "," + IMG);
        assertThat(back.extra().get("cover")).isEqualTo(IMG);
        Map<String, Object> asMap = om.readValue(body, new TypeReference<>() { });
        assertThat(asMap.get("avatarUrl")).isEqualTo(IMG);
    }

    @Test
    void 入参_未签名的上传地址拒绝() {
        assertThatThrownBy(() -> om.readValue("{\"avatarUrl\":\"" + IMG + "\"}", Profile.class))
                .isInstanceOf(JsonMappingException.class)
                .hasRootCauseInstanceOf(UploadUrlRejectedException.class);
    }
}
