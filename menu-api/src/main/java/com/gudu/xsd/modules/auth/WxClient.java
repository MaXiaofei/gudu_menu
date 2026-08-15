package com.gudu.xsd.modules.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gudu.xsd.common.BizException;
import com.gudu.xsd.config.WxProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 微信服务端接口客户端。
 *
 * <p>code2session：wx.login 的 code → openid（登录用，不需要 access_token）。
 * <p>getAccessToken：微信服务端接口凭证（7200s 有效 + 每日获取上限），
 * 按官方要求中控缓存——Redis key = wx:access_token:{appid}（预发/生产各自独立），
 * TTL 3600s（留 1 小时刷新余量）。
 */
@Component
@RequiredArgsConstructor
public class WxClient {

    private final WxProperties props;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();

    private static final String CODE2SESSION_URL = "https://api.weixin.qq.com/sns/jscode2session";
    private static final String TOKEN_URL = "https://api.weixin.qq.com/cgi-bin/token";

    /** wx.login 的 code 换 openid。code 一次性、5 分钟有效。 */
    public String code2session(String code) {
        requireConfig();
        String url = CODE2SESSION_URL
                + "?appid=" + enc(props.getAppid())
                + "&secret=" + enc(props.getSecret())
                + "&js_code=" + enc(code)
                + "&grant_type=authorization_code";
        JsonNode r = getJson(url);
        if (r.path("openid").isMissingNode() || r.path("openid").asText().isEmpty()) {
            // 常见 errcode：40029 invalid code / 45011 频率限制 / -1 系统繁忙
            throw new BizException("微信登录失败(" + r.path("errcode").asInt(-1) + ")：" + r.path("errmsg").asText("code 无效"));
        }
        return r.get("openid").asText();
    }

    /**
     * 微信服务端接口凭证（小程序码/订阅消息等用）。中控缓存：
     * Redis key wx:access_token:{appid}，TTL 3600s（官方有效期 7200s，提前刷新）。
     */
    public String getAccessToken() {
        requireConfig();
        String key = "wx:access_token:" + props.getAppid();
        String cached = redis.opsForValue().get(key);
        if (cached != null && !cached.isEmpty()) {
            return cached;
        }
        JsonNode r = getJson(TOKEN_URL
                + "?grant_type=client_credential"
                + "&appid=" + enc(props.getAppid())
                + "&secret=" + enc(props.getSecret()));
        if (r.path("access_token").isMissingNode() || r.path("access_token").asText().isEmpty()) {
            throw new BizException("获取微信 access_token 失败(" + r.path("errcode").asInt(-1) + ")：" + r.path("errmsg").asText(""));
        }
        String token = r.get("access_token").asText();
        redis.opsForValue().set(key, token, Duration.ofSeconds(3600));
        return token;
    }

    private void requireConfig() {
        if (props.getAppid().isEmpty() || props.getSecret().isEmpty()) {
            throw new BizException("微信登录未配置（WX_APPID/WX_APPSECRET）");
        }
    }

    private JsonNode getJson(String url) {
        try {
            HttpResponse<String> resp = http.send(
                    HttpRequest.newBuilder(URI.create(url)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            return objectMapper.readTree(resp.body());
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("微信接口调用失败：" + e.getMessage());
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
