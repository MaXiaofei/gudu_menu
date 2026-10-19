package com.gudu.xsd.common;

/**
 * 业务异常。默认 code = 1；鉴权/限流类用 {@link #forbidden}/{@link #tooManyRequests} 携带对应业务码。
 */
public class BizException extends RuntimeException {

    /** 已登录但无权限。 */
    public static final int FORBIDDEN = 403;
    /** 调用频率/额度超限。 */
    public static final int TOO_MANY_REQUESTS = 429;

    private final int code;

    public BizException(String msg) {
        this(1, msg);
    }

    public BizException(int code, String msg) {
        super(msg);
        this.code = code;
    }

    public static BizException forbidden(String msg) {
        return new BizException(FORBIDDEN, msg);
    }

    public static BizException tooManyRequests(String msg) {
        return new BizException(TOO_MANY_REQUESTS, msg);
    }

    public int getCode() {
        return code;
    }
}
