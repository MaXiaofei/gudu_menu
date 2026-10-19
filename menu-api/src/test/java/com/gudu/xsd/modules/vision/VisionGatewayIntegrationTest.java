package com.gudu.xsd.modules.vision;

import com.gudu.xsd.modules.ai.gateway.application.ModelGateway;
import com.gudu.xsd.modules.ai.gateway.application.ModelUnavailableException;
import com.gudu.xsd.modules.ai.gateway.domain.model.ChatResult;
import com.gudu.xsd.modules.ai.gateway.domain.model.ModelReply;
import com.gudu.xsd.modules.vision.application.GatewayVisionModelAdapter;
import com.gudu.xsd.modules.vision.domain.model.RecognitionResult;
import com.gudu.xsd.modules.vision.domain.model.VisionJob;
import com.gudu.xsd.modules.vision.domain.model.VisionMode;
import com.gudu.xsd.modules.vision.domain.port.ImageStore;
import com.gudu.xsd.modules.vision.domain.port.VisionModelPort;
import com.gudu.xsd.modules.vision.infrastructure.GatewayVisionProvider;
import com.gudu.xsd.modules.vision.infrastructure.MockVisionProvider;
import com.gudu.xsd.modules.vision.infrastructure.RoutingVisionProvider;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 拍照识别接入模型网关：本站图片转 base64、降级标记、网关失败退回示例识别。 */
class VisionGatewayIntegrationTest {

    static final String FRIDGE_JSON = "{\"items\":[{\"name\":\"西红柿\",\"amount\":2,\"unit\":\"个\",\"confidence\":0.95}]}";

    private final ModelGateway gateway = mock(ModelGateway.class);
    private final ImageStore images = mock(ImageStore.class);

    private static ChatResult result(String text, boolean degraded) {
        return new ChatResult(ModelReply.text(text), "deepseek", "deepseek-flash", degraded, 900, BigDecimal.ZERO, List.of());
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<VisionModelPort> provider(VisionModelPort port) {
        ObjectProvider<VisionModelPort> p = mock(ObjectProvider.class);
        when(p.getIfAvailable()).thenReturn(port);
        return p;
    }

    @Test
    void 本站上传图片读成base64交给网关_公网URL原样传() {
        when(images.read("/gudu/uploads/original/a.png")).thenReturn(Optional.of("png".getBytes(StandardCharsets.UTF_8)));
        when(gateway.vision(anyString(), anyString(), anyString(), anyString(), isNull())).thenReturn(result(FRIDGE_JSON, false));
        GatewayVisionModelAdapter adapter = new GatewayVisionModelAdapter(gateway, images);

        VisionModelPort.Output out = adapter.recognize("/gudu/uploads/original/a.png", "识别", "{}");
        assertThat(out.text()).isEqualTo(FRIDGE_JSON);
        assertThat(out.degraded()).isFalse();
        ArgumentCaptor<String> image = ArgumentCaptor.forClass(String.class);
        verify(gateway).vision(eq("vision"), image.capture(), eq("识别"), eq("{}"), isNull());
        assertThat(image.getValue()).startsWith("data:image/png;base64,");

        adapter.recognize("https://staging.imxf.cloud/images/a.jpg", "识别", "{}");
        verify(gateway).vision(eq("vision"), eq("https://staging.imxf.cloud/images/a.jpg"), any(), any(), isNull());

        when(images.read("/gudu/uploads/original/gone.jpg")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> adapter.recognize("/gudu/uploads/original/gone.jpg", "识别", "{}"))
                .hasMessageContaining("不存在");
    }

    @Test
    void 网关可用_结果按解析器对齐_后备模型时标记降级() {
        when(gateway.vision(anyString(), anyString(), anyString(), anyString(), isNull()))
                .thenReturn(result(FRIDGE_JSON, false), result(FRIDGE_JSON, true));
        GatewayVisionModelAdapter adapter = new GatewayVisionModelAdapter(gateway, images);
        RoutingVisionProvider routing = new RoutingVisionProvider(new GatewayVisionProvider(provider(adapter)),
                new MockVisionProvider());

        RecognitionResult first = routing.recognize(VisionMode.FRIDGE, "https://x.cn/a.jpg");
        assertThat(first.items()).extracting(i -> i.name()).containsExactly("西红柿");
        assertThat(first.degraded()).isFalse();
        assertThat(routing.recognize(VisionMode.FRIDGE, "https://x.cn/a.jpg").degraded()).isTrue();
        assertThat(routing.name()).isEqualTo("gateway");
    }

    @Test
    void 网关全部失败或输出不可解析_退回示例识别并标记降级() {
        when(gateway.vision(anyString(), anyString(), anyString(), anyString(), isNull()))
                .thenThrow(new ModelUnavailableException("模型暂不可用"));
        RoutingVisionProvider routing = new RoutingVisionProvider(
                new GatewayVisionProvider(provider(new GatewayVisionModelAdapter(gateway, images))), new MockVisionProvider());
        RecognitionResult r = routing.recognize(VisionMode.FRIDGE, "https://x.cn/a.jpg");
        assertThat(r.degraded()).isTrue();
        assertThat(r.items()).isNotEmpty();

        RoutingVisionProvider noPort = new RoutingVisionProvider(new GatewayVisionProvider(provider(null)),
                new MockVisionProvider());
        assertThat(noPort.recognize(VisionMode.RECEIPT, "/gudu/uploads/original/a.jpg").degraded()).isTrue();
        assertThat(noPort.name()).isEqualTo("mock");
    }

    @Test
    void 任务记录降级标记() {
        VisionJob job = VisionJob.create(1L, VisionMode.FRIDGE, "/gudu/uploads/original/a.jpg", LocalDateTime.now());
        job.complete(List.of(), null, true);
        assertThat(job.degraded()).isTrue();
        VisionJob plain = VisionJob.create(1L, VisionMode.FRIDGE, "/gudu/uploads/original/b.jpg", LocalDateTime.now());
        plain.complete(List.of(), null);
        assertThat(plain.degraded()).isFalse();
    }
}
