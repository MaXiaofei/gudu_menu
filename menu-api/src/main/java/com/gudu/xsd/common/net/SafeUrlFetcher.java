package com.gudu.xsd.common.net;

import com.gudu.xsd.common.BizException;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Locale;

/**
 * 防 SSRF 的外链抓取：只抓公网 http(s) 页面。
 *
 * <ul>
 *   <li>协议仅 http/https，端口仅 80/443（或缺省）；</li>
 *   <li>主机解析后的<b>每个</b> IP 都必须是公网地址（拒回环/私网/链路本地/CGNAT/组播/保留/云元数据）；</li>
 *   <li>不自动跟随重定向，手动跟随 ≤ {@link #MAX_REDIRECTS} 跳并逐跳复检；</li>
 *   <li>响应体 ≤ 2MB，超时 8s。</li>
 * </ul>
 * 已知局限：校验与连接之间存在 DNS 重绑定窗口；生产再由出口网络策略兜底。
 */
public final class SafeUrlFetcher {

    static final int MAX_REDIRECTS = 3;
    private static final int TIMEOUT_MS = 8_000;
    private static final int MAX_BODY_BYTES = 2 * 1024 * 1024;
    private static final String REJECT_MSG = "不支持的链接，请粘贴公开菜谱网页地址";

    /** 可替换的 DNS 解析（单测注入）。 */
    @FunctionalInterface
    public interface Resolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    private final Resolver resolver;
    private final String userAgent;

    public SafeUrlFetcher(String userAgent) {
        this(InetAddress::getAllByName, userAgent);
    }

    public SafeUrlFetcher(Resolver resolver, String userAgent) {
        this.resolver = resolver;
        this.userAgent = userAgent;
    }

    /** 抓取并解析 HTML。网络/HTTP 层异常原样抛出（IOException），由调用方翻译成用户提示。 */
    public Document fetch(String url) throws IOException {
        URI current = validate(url);
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            Connection.Response resp = Jsoup.connect(current.toString())
                    .userAgent(userAgent)
                    .timeout(TIMEOUT_MS)
                    .maxBodySize(MAX_BODY_BYTES)
                    .followRedirects(false)
                    .ignoreHttpErrors(false)
                    .header("Accept-Language", "zh-CN,zh;q=0.9")
                    .execute();
            int code = resp.statusCode();
            if (code >= 300 && code < 400) {
                String location = resp.header("Location");
                if (location == null || location.isBlank()) throw new BizException(REJECT_MSG);
                current = validate(current.resolve(location.trim()).toString());
                continue;
            }
            return resp.parse();
        }
        throw new BizException("链接重定向次数过多");
    }

    /** 校验 URL 并返回规范化 URI；不合规抛 BizException。 */
    URI validate(String url) {
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new BizException("URL 格式不正确");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) throw new BizException(REJECT_MSG);
        if (uri.getUserInfo() != null) throw new BizException(REJECT_MSG);
        int port = uri.getPort();
        if (port != -1 && port != 80 && port != 443) throw new BizException(REJECT_MSG);
        String host = uri.getHost();
        if (host == null || host.isBlank()) throw new BizException("URL 格式不正确");
        InetAddress[] addrs;
        try {
            addrs = resolver.resolve(host);
        } catch (UnknownHostException e) {
            throw new BizException("无法访问该网址");
        }
        if (addrs.length == 0) throw new BizException("无法访问该网址");
        for (InetAddress a : addrs) {
            if (!isPublic(a)) throw new BizException(REJECT_MSG);
        }
        return uri;
    }

    static boolean isPublic(InetAddress a) {
        if (a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress()
                || a.isSiteLocalAddress() || a.isMulticastAddress()) {
            return false;
        }
        byte[] b = a.getAddress();
        if (a instanceof Inet4Address) {
            int b0 = b[0] & 0xff, b1 = b[1] & 0xff;
            if (b0 == 0) return false;                               // 0.0.0.0/8
            if (b0 == 100 && b1 >= 64 && b1 <= 127) return false;    // 100.64/10 CGNAT
            if (b0 == 192 && b1 == 0 && (b[2] & 0xff) == 0) return false; // 192.0.0/24
            if (b0 == 198 && (b1 == 18 || b1 == 19)) return false;   // 198.18/15 基准测试
            return b0 < 224;                                         // 224+ 组播/保留
        }
        if (a instanceof Inet6Address) {
            int b0 = b[0] & 0xff;
            if ((b0 & 0xfe) == 0xfc) return false;                   // fc00::/7 ULA
            // IPv4 映射地址 ::ffff:a.b.c.d → 按 IPv4 规则判
            boolean mapped = true;
            for (int i = 0; i < 10; i++) if (b[i] != 0) { mapped = false; break; }
            if (mapped && (b[10] & 0xff) == 0xff && (b[11] & 0xff) == 0xff) {
                try {
                    return isPublic(InetAddress.getByAddress(new byte[]{b[12], b[13], b[14], b[15]}));
                } catch (UnknownHostException e) {
                    return false;
                }
            }
        }
        return true;
    }
}
