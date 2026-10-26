package com.gudu.xsd.modules.contentsecurity.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gudu.xsd.common.BizException;
import com.gudu.xsd.modules.auth.WxClient;
import com.gudu.xsd.modules.contentsecurity.domain.model.ContentScene;
import com.gudu.xsd.modules.contentsecurity.domain.model.MediaCheckRequest;
import com.gudu.xsd.modules.contentsecurity.domain.model.Suggest;
import com.gudu.xsd.modules.contentsecurity.domain.model.TextCheck;
import com.gudu.xsd.modules.contentsecurity.domain.model.TextVerdict;
import com.gudu.xsd.modules.contentsecurity.domain.port.ContentSecurityUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 微信客户端（WxClient / HTTP）全部 mock，不发真实请求。 */
class WxContentSecurityTest {

    static final ObjectMapper OM = new ObjectMapper();

    WxClient wxClient;
    WxHttp http;
    WxContentSecurity cs;

    @BeforeEach
    void setUp() {
        wxClient = mock(WxClient.class);
        when(wxClient.getAccessToken()).thenReturn("AT");
        http = mock(WxHttp.class);
        cs = new WxContentSecurity(wxClient, http);
    }

    static JsonNode json(String s) throws IOException {
        return OM.readTree(s);
    }

    static JsonNode result(String suggest, int label) throws IOException {
        return json("{\"errcode\":0,\"errmsg\":\"ok\",\"trace_id\":\"tr-" + suggest + "\",\"result\":{\"suggest\":\""
                + suggest + "\",\"label\":" + label + "}}");
    }

    @Test
    @SuppressWarnings("unchecked")
    void 文本通过_v2请求体含openid与场景() throws Exception {
        when(http.postJson(anyString(), any())).thenReturn(result("pass", 100));
        TextVerdict v = cs.checkText(new TextCheck("番茄炒蛋", ContentScene.PROFILE, "o1"));
        assertThat(v.suggest()).isEqualTo(Suggest.PASS);
        assertThat(v.source()).isEqualTo(TextVerdict.Source.WX);
        ArgumentCaptor<Object> body = ArgumentCaptor.forClass(Object.class);
        verify(http).postJson(startsWith(WxContentSecurity.MSG_SEC_CHECK + "AT"), body.capture());
        Map<String, Object> b = (Map<String, Object>) body.getValue();
        assertThat(b).containsEntry("version", 2).containsEntry("scene", 1).containsEntry("openid", "o1")
                .containsEntry("content", "番茄炒蛋");
    }

    @Test
    void 文本违规_返回RISKY与标签() throws Exception {
        when(http.postJson(anyString(), any())).thenReturn(result("risky", 20002));
        TextVerdict v = cs.checkText(new TextCheck("x", ContentScene.COMMENT, "o1"));
        assertThat(v.suggest()).isEqualTo(Suggest.RISKY);
        assertThat(v.label()).isEqualTo(20002);
        assertThat(v.traceId()).isEqualTo("tr-risky");
    }

    @Test
    void 超长文本分段_取最严结论_遇违规即停() throws Exception {
        when(http.postJson(anyString(), any())).thenReturn(result("pass", 100), result("review", 10001),
                result("risky", 20001));
        String text = "菜".repeat(WxContentSecurity.MAX_CHARS * 3 + 1);
        TextVerdict v = cs.checkText(new TextCheck(text, ContentScene.FORUM, "o1"));
        assertThat(v.suggest()).isEqualTo(Suggest.RISKY);
        assertThat(v.label()).isEqualTo(20001);
        verify(http, times(3)).postJson(anyString(), any());
        assertThat(WxContentSecurity.chunks(text)).hasSize(4);
    }

    @Test
    void 不可用_业务错误码_网络超时_取token失败_无openid() throws Exception {
        when(http.postJson(anyString(), any())).thenReturn(json("{\"errcode\":61010,\"errmsg\":\"user access record expired\"}"));
        assertThatThrownBy(() -> cs.checkText(new TextCheck("x", ContentScene.PROFILE, "o1")))
                .isInstanceOf(ContentSecurityUnavailableException.class).hasMessageContaining("61010");

        when(http.postJson(anyString(), any())).thenThrow(new HttpTimeoutException("timed out"));
        assertThatThrownBy(() -> cs.checkText(new TextCheck("x", ContentScene.PROFILE, "o1")))
                .isInstanceOf(ContentSecurityUnavailableException.class);

        when(wxClient.getAccessToken()).thenThrow(new BizException("微信登录未配置（WX_APPID/WX_APPSECRET）"));
        assertThatThrownBy(() -> cs.checkText(new TextCheck("x", ContentScene.PROFILE, "o1")))
                .isInstanceOf(ContentSecurityUnavailableException.class);

        assertThatThrownBy(() -> cs.checkText(new TextCheck("x", ContentScene.PROFILE, null)))
                .isInstanceOf(ContentSecurityUnavailableException.class);
    }

    @Test
    void token失效_清缓存重试一次() throws Exception {
        when(http.postJson(anyString(), any())).thenReturn(json("{\"errcode\":40001,\"errmsg\":\"invalid credential\"}"),
                result("pass", 100));
        assertThat(cs.checkText(new TextCheck("x", ContentScene.PROFILE, "o1")).suggest()).isEqualTo(Suggest.PASS);
        verify(wxClient).invalidateAccessToken();
        verify(http, times(2)).postJson(anyString(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void 图片异步提交_返回traceId_请求体为v2图片() throws Exception {
        when(http.postJson(anyString(), any())).thenReturn(json("{\"errcode\":0,\"errmsg\":\"ok\",\"trace_id\":\"T9\"}"));
        assertThat(cs.submitMedia(new MediaCheckRequest("https://imxf.cloud/gudu/uploads/a.jpg", ContentScene.SOCIAL_LOG,
                "o1"))).isEqualTo("T9");
        ArgumentCaptor<Object> body = ArgumentCaptor.forClass(Object.class);
        verify(http).postJson(startsWith(WxContentSecurity.MEDIA_CHECK_ASYNC + "AT"), body.capture());
        assertThat((Map<String, Object>) body.getValue()).containsEntry("media_type", 2).containsEntry("version", 2)
                .containsEntry("scene", 4).containsEntry("media_url", "https://imxf.cloud/gudu/uploads/a.jpg");
    }

    @Test
    void 图片提交失败_不可用() throws Exception {
        when(http.postJson(anyString(), any())).thenReturn(json("{\"errcode\":-1,\"errmsg\":\"system error\"}"));
        assertThatThrownBy(() -> cs.submitMedia(new MediaCheckRequest("u", ContentScene.PROFILE, "o1")))
                .isInstanceOf(ContentSecurityUnavailableException.class);
        verify(wxClient, never()).invalidateAccessToken();
    }
}
