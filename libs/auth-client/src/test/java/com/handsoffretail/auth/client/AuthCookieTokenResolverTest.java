package com.handsoffretail.auth.client;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class AuthCookieTokenResolverTest {
    private final AuthCookieTokenResolver resolver = new AuthCookieTokenResolver();

    @Test
    void resolvesAccessCookieAndIgnoresAuthorizationHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer ignored");
        String cookieValue = UUID.randomUUID().toString();
        request.setCookies(new Cookie("hor_access", cookieValue));

        assertThat(resolver.resolve(request)).isEqualTo(cookieValue);
    }

    @Test
    void returnsNullWhenCookieIsMissing() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertThat(resolver.resolve(request)).isNull();
    }
}
