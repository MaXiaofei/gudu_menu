package com.gudu.xsd.modules.ai.gateway.domain.port;

/**
 * 传输失败。{@code retryable}：超时、连接失败、429、5xx 可在同一目标重试；4xx（鉴权、参数）直接换下一个目标。
 * {@code imageFetchFailed}：供应商下载图片失败（视觉请求可改用 base64 重发）。
 */
public class TransportException extends RuntimeException {

    private final boolean retryable;
    private final boolean imageFetchFailed;
    private final int status;

    public TransportException(String message, boolean retryable, int status, boolean imageFetchFailed) {
        super(message);
        this.retryable = retryable;
        this.status = status;
        this.imageFetchFailed = imageFetchFailed;
    }

    public static TransportException timeout(String message) {
        return new TransportException(message, true, 0, false);
    }

    public boolean retryable() {
        return retryable;
    }

    public boolean imageFetchFailed() {
        return imageFetchFailed;
    }

    public int status() {
        return status;
    }
}
