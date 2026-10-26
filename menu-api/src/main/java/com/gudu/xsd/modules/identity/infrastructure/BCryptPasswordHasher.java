package com.gudu.xsd.modules.identity.infrastructure;

import com.gudu.xsd.modules.identity.domain.service.PasswordHasher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/** BCrypt 实现（与 V29 起 member.password_hash 同算法，迁移后原哈希直接可用）。 */
@Component
public class BCryptPasswordHasher implements PasswordHasher {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Override
    public String hash(String raw) {
        return encoder.encode(raw);
    }

    @Override
    public boolean matches(String raw, String hash) {
        try {
            return raw != null && hash != null && encoder.matches(raw, hash);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
