package com.handsoff.hor.auth.config;

import jakarta.validation.constraints.NotNull;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("hor.auth")
public record AuthProperties(
        @NotNull Path signingKeyPath,
        @NotNull Duration accessTokenTtl,
        @NotNull Duration refreshTokenTtl,
        int maxFailedLogins,
        @NotNull Duration lockDuration,
        int bcryptStrength,
        String cookieDomain,
        @NotNull List<String> allowedOrigins) {
}
