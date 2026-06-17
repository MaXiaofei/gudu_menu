package com.yanhuo.xsd.common;

/**
 * 业务异常。默认 code = 1。
 */
public class BizException extends RuntimeException {

    private final int code;

    public BizException(String msg) {
        super(msg);
        this.code = 1;
    }

    public int getCode() {
        return code;
    }
}
