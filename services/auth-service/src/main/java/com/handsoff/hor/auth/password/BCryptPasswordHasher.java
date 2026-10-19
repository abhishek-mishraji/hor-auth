package com.handsoff.hor.auth.password;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public final class BCryptPasswordHasher implements PasswordHasher {
    private final BCryptPasswordEncoder encoder;

    public BCryptPasswordHasher(com.handsoff.hor.auth.config.AuthProperties properties) {
        encoder = new BCryptPasswordEncoder(properties.bcryptStrength());
    }

    @Override
    public String hash(String password) {
        return encoder.encode(password);
    }

    @Override
    public boolean matches(String password, String encodedPassword) {
        return encoder.matches(password, encodedPassword);
    }
}
