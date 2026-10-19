package com.gudu.xsd.common.net;

import com.gudu.xsd.common.BizException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** SSRF 防护：协议/端口/内网地址/域名解析到内网全部拒绝（离线，注入解析器）。 */
class SafeUrlFetcherTest {

    /** 解析器：evil.example 解析到内网，其余按字面量解析（不联网）。 */
    private final SafeUrlFetcher fetcher = new SafeUrlFetcher(host -> {
        if (host.equals("evil.example")) return new InetAddress[]{InetAddress.getByName("10.0.0.8")};
        if (host.equals("mixed.example")) return new InetAddress[]{
                InetAddress.getByName("93.184.216.34"), InetAddress.getByName("127.0.0.1")};
        if (host.equals("www.xiachufang.com")) return new InetAddress[]{InetAddress.getByName("101.200.1.1")};
        if (host.matches("[0-9.]+|\\[?[0-9a-fA-F:.]+]?")) return new InetAddress[]{InetAddress.getByName(host)};
        throw new UnknownHostException(host);
    }, "test-ua");

    @ParameterizedTest
    @ValueSource(strings = {
            "http://127.0.0.1:11434/api/tags",
            "http://127.0.0.1/",
            "http://10.1.2.3/",
            "http://172.16.0.5/",
            "http://192.168.1.1/",
            "http://169.254.169.254/latest/meta-data/",
            "http://100.64.0.1/",
            "http://0.0.0.0/",
            "http://[::1]/",
            "http://[fd00::1]/",
            "http://[::ffff:127.0.0.1]/",
            "http://evil.example/recipe",
            "http://mixed.example/recipe",
            "file:///etc/passwd",
            "gopher://example.com/",
            "ftp://example.com/",
            "https://www.xiachufang.com:8080/recipe/1",
            "http://user:pass@www.xiachufang.com/",
    })
    void 拒绝非公网或非法链接(String url) {
        assertThatThrownBy(() -> fetcher.validate(url)).isInstanceOf(BizException.class);
    }

    @Test
    void 未知域名_拒绝() {
        assertThatThrownBy(() -> fetcher.validate("https://nope.invalid/x"))
                .isInstanceOf(BizException.class).hasMessageContaining("无法访问");
    }

    @Test
    void 公网菜谱站_通过() {
        assertThat(fetcher.validate("https://www.xiachufang.com/recipe/1/").getHost())
                .isEqualTo("www.xiachufang.com");
    }

    @Test
    void 公网IPv4判定() throws Exception {
        assertThat(SafeUrlFetcher.isPublic(InetAddress.getByName("101.200.1.1"))).isTrue();
        assertThat(SafeUrlFetcher.isPublic(InetAddress.getByName("100.128.0.1"))).isTrue();
        assertThat(SafeUrlFetcher.isPublic(InetAddress.getByName("100.100.0.1"))).isFalse();
        assertThat(SafeUrlFetcher.isPublic(InetAddress.getByName("224.0.0.1"))).isFalse();
    }
}
