package com.gudu.xsd.modules.vision.application;

import com.gudu.xsd.modules.ai.gateway.application.ModelGateway;
import com.gudu.xsd.modules.ai.gateway.domain.model.ChatResult;
import com.gudu.xsd.modules.vision.domain.port.ImageStore;
import com.gudu.xsd.modules.vision.domain.port.VisionModelPort;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.Locale;

/**
 * 视觉端口 → 模型网关 VISION 能力（默认 deepseek-flash，detail=low 由网关处理）。
 * 本站上传图片读本地文件转 base64 data URL（staging/本地的上传目录不一定公网可达）；公网 URL 原样交给网关，
 * 网关下载失败时自行内联重试。真实模型全部失败时网关抛异常，由 {@code RoutingVisionProvider} 退回 Mock。
 */
@Component
public class GatewayVisionModelAdapter implements VisionModelPort {

    static final String SCENE = "vision";

    private final ModelGateway gateway;
    private final ImageStore imageStore;

    public GatewayVisionModelAdapter(ModelGateway gateway, ImageStore imageStore) {
        this.gateway = gateway;
        this.imageStore = imageStore;
    }

    @Override
    public Output recognize(String imageUrl, String prompt, String jsonSchema) {
        ChatResult r = gateway.vision(SCENE, toModelImage(imageUrl), prompt, jsonSchema, null);
        return new Output(r.content(), r.degraded(), r.model());
    }

    String toModelImage(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) throw new IllegalArgumentException("缺少识别图片");
        String u = imageUrl.trim();
        if (u.startsWith("http://") || u.startsWith("https://") || u.startsWith("data:")) return u;
        byte[] bytes = imageStore.read(u).orElseThrow(() -> new IllegalStateException("识别原图不存在或已清理"));
        return "data:" + mimeOf(u) + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }

    static String mimeOf(String path) {
        String p = path.toLowerCase(Locale.ROOT);
        if (p.endsWith(".png")) return "image/png";
        if (p.endsWith(".webp")) return "image/webp";
        if (p.endsWith(".gif")) return "image/gif";
        return "image/jpeg";
    }
}
