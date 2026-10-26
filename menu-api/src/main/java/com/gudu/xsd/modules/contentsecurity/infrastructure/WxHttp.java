package com.gudu.xsd.modules.contentsecurity.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** 微信服务端 JSON POST（独立出来便于单测 mock；连接 3s、整体 5s 超时，超时即视为不可用）。 */
@Component
public class WxHttp {

    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    public JsonNode postJson(String url, Object body) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() / 100 != 2) throw new IOException("HTTP " + resp.statusCode());
        return JSON.readTree(resp.body());
    }
}
