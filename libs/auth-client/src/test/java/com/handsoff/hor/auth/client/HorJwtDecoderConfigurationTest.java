package com.handsoff.hor.auth.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.security.SecureRandom;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

class HorJwtDecoderConfigurationTest {
    private static final Instant NOW = Instant.parse("2026-10-20T00:00:00Z");
    private static KeyPair trustedKeyPair;
    private static HttpServer jwksServer;
    private static String jwksUri;

    @BeforeAll
    static void startJwksServer() throws Exception {
        trustedKeyPair = keyPair();
        RSAKey jwk = new RSAKey.Builder((java.security.interfaces.RSAPublicKey) trustedKeyPair.getPublic())
                .keyID("test-key")
                .build();
        byte[] jwks = new JWKSet(jwk).toString().getBytes(StandardCharsets.UTF_8);
        jwksServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        jwksServer.createContext("/jwks", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, jwks.length);
            exchange.getResponseBody().write(jwks);
            exchange.close();
        });
        jwksServer.start();
        jwksUri = "http://127.0.0.1:" + jwksServer.getAddress().getPort() + "/jwks";
    }

    @AfterAll
    static void stopJwksServer() {
        jwksServer.stop(0);
    }

    @Test
    void acceptsValidRs256TokenAndRejectsWrongIssuer() throws Exception {
        JwtDecoder decoder = decoder();
        Jwt accepted = decoder.decode(token(trustedKeyPair, "hor-auth", NOW.plusSeconds(60), JWSAlgorithm.RS256));
        assertThat(accepted.getSubject()).isEqualTo("7");

        assertThatThrownBy(() -> decoder.decode(token(trustedKeyPair, "wrong-issuer", NOW.plusSeconds(60), JWSAlgorithm.RS256)))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsExpiredTokenBeyondClockSkew() throws Exception {
        String expired = token(trustedKeyPair, "hor-auth", NOW.minusSeconds(31), JWSAlgorithm.RS256);

        assertThatThrownBy(() -> decoder().decode(expired)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsTokenSignedByWrongKey() throws Exception {
        String signedWithOtherKey = token(keyPair(), "hor-auth", NOW.plusSeconds(60), JWSAlgorithm.RS256);

        assertThatThrownBy(() -> decoder().decode(signedWithOtherKey)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsNoneAndHmacAlgorithms() throws Exception {
        JWTClaimsSet claims = claims("hor-auth", NOW.plusSeconds(60));
        String encodedHeader = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        String encodedClaims = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(claims.toJSONObject().toString().getBytes(StandardCharsets.UTF_8));
        String none = encodedHeader + "." + encodedClaims + ".";
        SignedJWT hmac = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        byte[] hmacKey = new byte[32];
        new SecureRandom().nextBytes(hmacKey);
        hmac.sign(new MACSigner(hmacKey));

        assertThatThrownBy(() -> decoder().decode(none)).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> decoder().decode(hmac.serialize())).isInstanceOf(JwtException.class);
    }

    private static JwtDecoder decoder() {
        return HorJwtDecoderConfiguration.create(jwksUri, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static String token(KeyPair keyPair, String issuer, Instant expiration, JWSAlgorithm algorithm)
            throws JOSEException {
        RSAKey signingKey = new RSAKey.Builder((java.security.interfaces.RSAPublicKey) keyPair.getPublic())
                .privateKey((java.security.interfaces.RSAPrivateKey) keyPair.getPrivate())
                .keyID("test-key")
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(algorithm).keyID("test-key").build(), claims(issuer, expiration));
        jwt.sign(new RSASSASigner(signingKey));
        return jwt.serialize();
    }

    private static JWTClaimsSet claims(String issuer, Instant expiration) {
        return new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject("7")
                .issueTime(Date.from(NOW))
                .expirationTime(Date.from(expiration))
                .jwtID("test-jti")
                .claim("typ", "ADMIN")
                .claim("role", "PLATFORM_ADMIN")
                .claim("name", "Test Admin")
                .build();
    }

    private static KeyPair keyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }
}
