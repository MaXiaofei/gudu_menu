package com.gudu.xsd.modules.contentsecurity.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.gudu.xsd.modules.auth.WxClient;
import com.gudu.xsd.modules.contentsecurity.domain.model.MediaCheckRequest;
import com.gudu.xsd.modules.contentsecurity.domain.model.Suggest;
import com.gudu.xsd.modules.contentsecurity.domain.model.TextCheck;
import com.gudu.xsd.modules.contentsecurity.domain.model.TextVerdict;
import com.gudu.xsd.modules.contentsecurity.domain.port.ContentSecurity;
import com.gudu.xsd.modules.contentsecurity.domain.port.ContentSecurityUnavailableException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 微信内容安全实现：
 * <ul>
 *   <li>文本 msgSecCheck v2（POST /wxa/msg_sec_check，需 openid + scene；单次 ≤ 2500 字，超长分段取最严结论）；</li>
 *   <li>图片 mediaCheckAsync v2（POST /wxa/media_check_async，media_type=2，结果经消息推送 wxa_media_check 回调）。</li>
 * </ul>
 * access_token 复用 {@link WxClient} 的 Redis 中控缓存；被判失效（40001 / 42001 / 40014）时清缓存重试一次。
 * 任何网络 / 凭证 / 业务错误码都抛 {@link ContentSecurityUnavailableException}，由应用层按用途决定拒绝或降级。
 */
@Component
public class WxContentSecurity implements ContentSecurity {

    static final String MSG_SEC_CHECK = "https://api.weixin.qq.com/wxa/msg_sec_check?access_token=";
    static final String MEDIA_CHECK_ASYNC = "https://api.weixin.qq.com/wxa/media_check_async?access_token=";
    static final int MAX_CHARS = 2500;
    static final int MEDIA_TYPE_IMAGE = 2;
    static final Set<Integer> TOKEN_INVALID = Set.of(40001, 42001, 40014);

    private final WxClient wxClient;
    private final WxHttp http;

    public WxContentSecurity(WxClient wxClient, WxHttp http) {
        this.wxClient = wxClient;
        this.http = http;
    }

    @Override
    public TextVerdict checkText(TextCheck check) {
        requireOpenid(check.openid());
        Suggest worst = null;
        int label = 100;
        String traceId = null;
        for (String chunk : chunks(check.content())) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("content", chunk);
            body.put("version", 2);
            body.put("scene", check.scene().wxCode());
            body.put("openid", check.openid());
            JsonNode r = call(MSG_SEC_CHECK, body);
            JsonNode result = r.path("result");
            if (result.isMissingNode()) throw new ContentSecurityUnavailableException("msgSecCheck 缺少 result");
            Suggest s = Suggest.ofWx(result.path("suggest").asText(null));
            if (worst == null || s.ordinal() > worst.ordinal()) {
                worst = s;
                label = result.path("label").asInt(100);
                traceId = r.path("trace_id").asText(null);
            }
            if (worst == Suggest.RISKY) break;
        }
        return new TextVerdict(worst, label, worst == Suggest.PASS ? null : "微信标签 " + label,
                TextVerdict.Source.WX, traceId);
    }

    @Override
    public String submitMedia(MediaCheckRequest request) {
        requireOpenid(request.openid());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("media_url", request.mediaUrl());
        body.put("media_type", MEDIA_TYPE_IMAGE);
        body.put("version", 2);
        body.put("scene", request.scene().wxCode());
        body.put("openid", request.openid());
        String traceId = call(MEDIA_CHECK_ASYNC, body).path("trace_id").asText("");
        if (traceId.isEmpty()) throw new ContentSecurityUnavailableException("mediaCheckAsync 未返回 trace_id");
        return traceId;
    }

    private JsonNode call(String api, Map<String, Object> body) {
        JsonNode r = post(api, body);
        int errcode = r.path("errcode").asInt(0);
        if (TOKEN_INVALID.contains(errcode)) {
            wxClient.invalidateAccessToken();
            r = post(api, body);
            errcode = r.path("errcode").asInt(0);
        }
        if (errcode != 0) {
            // 常见：61010 用户访问记录超时（openid 2 小时内未访问小程序）/ 45009 频率限制 / -1 系统繁忙
            throw new ContentSecurityUnavailableException("微信内容安全错误 " + errcode + "：" + r.path("errmsg").asText(""));
        }
        return r;
    }

    private JsonNode post(String api, Map<String, Object> body) {
        String token;
        try {
            token = wxClient.getAccessToken();
        } catch (RuntimeException e) {
            throw new ContentSecurityUnavailableException("获取 access_token 失败：" + e.getMessage(), e);
        }
        try {
            JsonNode r = http.postJson(api + token, body);
            if (r == null) throw new ContentSecurityUnavailableException("微信内容安全返回为空");
            return r;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ContentSecurityUnavailableException("微信内容安全调用被中断", e);
        } catch (ContentSecurityUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new ContentSecurityUnavailableException("微信内容安全调用失败：" + e.getMessage(), e);
        }
    }

    private static void requireOpenid(String openid) {
        if (openid == null || openid.isBlank()) throw new ContentSecurityUnavailableException("缺少 openid");
    }

    /** 按码点切成 ≤ {@value #MAX_CHARS} 字的段（不拆代理对）。 */
    static List<String> chunks(String text) {
        List<String> out = new ArrayList<>();
        int[] cps = text.codePoints().toArray();
        for (int i = 0; i < cps.length; i += MAX_CHARS) {
            out.add(new String(cps, i, Math.min(MAX_CHARS, cps.length - i)));
        }
        if (out.isEmpty()) out.add("");
        return out;
    }
}
