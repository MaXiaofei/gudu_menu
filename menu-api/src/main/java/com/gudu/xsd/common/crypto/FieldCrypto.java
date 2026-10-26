package com.gudu.xsd.common.crypto;

/**
 * {@link FieldCipher} 的全局持有者：MyBatis TypeHandler 由 MyBatis 实例化（非 Spring Bean），只能经静态入口取密钥。
 * 由 {@code FieldCryptoConfig} 在 SqlSessionFactory 创建前安装；未安装时任何加解密都抛异常（防止明文落库）。
 */
public final class FieldCrypto {

    private static volatile FieldCipher cipher;

    private FieldCrypto() {
    }

    public static void install(FieldCipher c) {
        cipher = c;
    }

    /** 仅单测使用：卸载。 */
    public static void uninstall() {
        cipher = null;
    }

    public static FieldCipher cipher() {
        FieldCipher c = cipher;
        if (c == null) throw new FieldCryptoException("字段加密未初始化（gudu.field-crypto.* 未配置）");
        return c;
    }

    public static String encrypt(String plain) {
        return plain == null ? null : cipher().encrypt(plain);
    }

    public static String decrypt(String value) {
        return FieldCipher.isEncrypted(value) ? cipher().decrypt(value) : value;
    }

    public static String blindIndex(String value) {
        return cipher().blindIndex(value);
    }
}
