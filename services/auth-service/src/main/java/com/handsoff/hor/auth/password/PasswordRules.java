package com.handsoff.hor.auth.password;

import com.handsoff.hor.auth.client.DomainException;
import com.handsoff.hor.auth.common.AuthErrorCode;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

@Component
public final class PasswordRules {
    private static final int MIN_CHARACTERS = 12;
    private static final int MAX_CHARACTERS = 72;
    private static final int MAX_UTF8_BYTES = 72;

    public void requireValid(String password) {
        int characterCount = password.codePointCount(0, password.length());
        int byteCount = password.getBytes(StandardCharsets.UTF_8).length;
        if (characterCount < MIN_CHARACTERS || characterCount > MAX_CHARACTERS || byteCount > MAX_UTF8_BYTES) {
            throw new DomainException(AuthErrorCode.VALIDATION_FAILED,
                    "Password must be 12–72 characters and no more than 72 UTF-8 bytes.");
        }
    }
}
