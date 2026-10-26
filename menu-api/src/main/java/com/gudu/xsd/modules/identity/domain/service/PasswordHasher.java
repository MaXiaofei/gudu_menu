package com.gudu.xsd.modules.identity.domain.service;

/** 密码哈希端口（实现：infrastructure BCrypt），领域层不依赖具体算法库。 */
public interface PasswordHasher {

    String hash(String raw);

    boolean matches(String raw, String hash);
}
