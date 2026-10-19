package com.gudu.xsd.modules.ai.gateway.infrastructure;

import com.gudu.xsd.modules.ai.gateway.domain.port.ImageInliner;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;

/**
 * 下载图片并转 data URL（≤ 4MB）。只允许 http(s) 公网地址，拒绝回环 / 内网 / 链路本地，防 SSRF。
 */
@Component
public class HttpImageInliner implements ImageInliner {

    static final int MAX_BYTES = 4 * 1024 * 1024;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    @Override
    public String toDataUrl(String imageUrl) {
        URI uri;
        try {
            uri = URI.create(imageUrl);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("图片地址不合法");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        if (!scheme.equals("http") && !scheme.equals("https")) throw new IllegalArgumentException("只支持 http(s) 图片");
        assertPublicHost(uri.getHost());
        try {
            HttpResponse<byte[]> resp = http.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15)).GET()
                    .build(), HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() / 100 != 2) throw new IllegalArgumentException("图片下载失败 HTTP " + resp.statusCode());
            String type = resp.headers().firstValue("Content-Type").orElse("image/jpeg").split(";")[0].trim();
            if (!type.startsWith("image/")) throw new IllegalArgumentException("不是图片：" + type);
            byte[] body = resp.body();
            if (body.length > MAX_BYTES) throw new IllegalArgumentException("图片超过 4MB");
            return "data:" + type + ";base64," + Base64.getEncoder().encodeToString(body);
        } catch (IOException e) {
            throw new IllegalArgumentException("图片下载失败：" + e.getClass().getSimpleName());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException("图片下载被中断");
        }
    }

    static void assertPublicHost(String host) {
        if (host == null || host.isBlank()) throw new IllegalArgumentException("图片地址缺少主机名");
        try {
            for (InetAddress a : InetAddress.getAllByName(host)) {
                if (a.isLoopbackAddress() || a.isSiteLocalAddress() || a.isLinkLocalAddress() || a.isAnyLocalAddress()
                        || a.isMulticastAddress()) {
                    throw new IllegalArgumentException("不允许访问内网地址");
                }
            }
        } catch (java.net.UnknownHostException e) {
            throw new IllegalArgumentException("无法解析图片主机");
        }
    }
}
