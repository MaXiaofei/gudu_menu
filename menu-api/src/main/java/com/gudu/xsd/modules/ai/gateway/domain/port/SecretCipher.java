package com.gudu.xsd.modules.ai.gateway.domain.port;

/** 供应商 key 加解密端口（AES-GCM，主密钥来自环境变量 GUDU_SECRET_KEY）。 */
public interface SecretCipher {

    /** 主密钥是否可用；不可用时只能使用环境变量来源的 key。 */
    boolean available();

    String encrypt(String plain);

    String decrypt(String cipherText);
}
