package com.gudu.xsd.common.crypto;

/** 字段加解密失败（密钥缺失 / 不匹配 / 密文损坏）。宁可报错也不返回错误数据。 */
public class FieldCryptoException extends RuntimeException {

    public FieldCryptoException(String message) {
        super(message);
    }

    public FieldCryptoException(String message, Throwable cause) {
        super(message, cause);
    }
}
