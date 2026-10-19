package com.handsoff.hor.auth.session;

import com.handsoff.hor.auth.config.AuthProperties;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public final class AuthCookies {
    private static final String ACCESS_COOKIE = "hor_access";
    private static final String REFRESH_COOKIE = "hor_refresh";
    private static final String ACCESS_PATH = "/";
    private static final String REFRESH_PATH = "/api/auth";
    private final AuthProperties properties;

    public AuthCookies(AuthProperties properties) {
        this.properties = properties;
    }

    public void write(HttpHeaders headers, String accessToken, String refreshToken) {
        headers.add(HttpHeaders.SET_COOKIE, cookie(ACCESS_COOKIE, accessToken, ACCESS_PATH,
                properties.accessTokenTtl()).build().toString());
        headers.add(HttpHeaders.SET_COOKIE, cookie(REFRESH_COOKIE, refreshToken, REFRESH_PATH,
                                properties.refreshTokenTtl()).build().toString());
    }

    public void clear(HttpHeaders headers) {
        headers.add(HttpHeaders.SET_COOKIE, cookie(ACCESS_COOKIE, "", ACCESS_PATH, Duration.ZERO).build().toString());
                headers.add(HttpHeaders.SET_COOKIE, cookie(REFRESH_COOKIE, "", REFRESH_PATH, Duration.ZERO).build().toString());
    }

    public void clear(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(ACCESS_COOKIE, "", ACCESS_PATH, Duration.ZERO).build().toString());
                response.addHeader(HttpHeaders.SET_COOKIE, cookie(REFRESH_COOKIE, "", REFRESH_PATH, Duration.ZERO).build().toString());
    }

    private ResponseCookie.ResponseCookieBuilder cookie(String name, String value, String path, Duration maxAge) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(name, value)
                .path(path)
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .maxAge(maxAge);
        if (properties.cookieDomain() != null && !properties.cookieDomain().isBlank()) {
            builder.domain(properties.cookieDomain());
        }
        return builder;
    }
}
