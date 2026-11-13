package com.handsoffretail.auth.client;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.security.interfaces.RSAPublicKey;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

public final class HorJwtDecoderConfiguration {
    private HorJwtDecoderConfiguration() {
    }

    public static JwtDecoder create(String jwksUri, Clock clock) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwksUri)
                .jwsAlgorithm(org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.RS256)
                .build();
        decoder.setJwtValidator(validators(clock));
        return decoder;
    }

    public static JwtDecoder create(RSAPublicKey publicKey, Clock clock) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey)
                .signatureAlgorithm(org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.RS256)
                .build();
        decoder.setJwtValidator(validators(clock));
        return decoder;
    }

    private static DelegatingOAuth2TokenValidator<Jwt> validators(Clock clock) {
        JwtTimestampValidator timestampValidator = new JwtTimestampValidator(Duration.ofSeconds(30));
        timestampValidator.setClock(clock);
        return new DelegatingOAuth2TokenValidator<>(
                timestampValidator,
                new JwtIssuerValidator("hor-auth"),
                requiredClaimsValidator());
    }

    private static OAuth2TokenValidator<Jwt> requiredClaimsValidator() {
        List<String> required = List.of("sub", "typ", "role", "name", "iat", "exp", "jti");
        return jwt -> {
            boolean present = required.stream().allMatch(name -> jwt.getClaims().containsKey(name));
            if (present) {
                return OAuth2TokenValidatorResult.success();
            }
            OAuth2Error error = new OAuth2Error("invalid_token", "Required claim missing.", null);
            return OAuth2TokenValidatorResult.failure(error);
        };
    }
}
