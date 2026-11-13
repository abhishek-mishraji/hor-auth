package com.handsoffretail.auth.token;

import com.handsoffretail.auth.account.AccountType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

@Component
public final class RefreshTokens {
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenValue generate(AccountType accountType) {
        byte[] random = new byte[32];
        secureRandom.nextBytes(random);
        String prefix = accountType == AccountType.ADMIN ? "a." : "u.";
        String value = prefix + Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        return new RefreshTokenValue(value, hash(value));
    }

    public byte[] hash(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }
}
