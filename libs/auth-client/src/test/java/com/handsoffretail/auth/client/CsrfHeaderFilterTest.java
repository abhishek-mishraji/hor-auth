package com.handsoffretail.auth.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CsrfHeaderFilterTest {
    private CsrfHeaderFilter filter() {
        HorAuthClientProperties properties = new HorAuthClientProperties();
        properties.setAllowedOrigins(List.of("http://localhost:5180", "https://auth.handsoffretail.com"));
        return new CsrfHeaderFilter(properties);
    }

    @Test
    void rejectsWriteWithoutRequiredHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/write");
        request.addHeader("Origin", "http://localhost:5180");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(request, response, (req, res) -> {
        });

        assertProblem(response, 403, "CSRF_REJECTED");
    }

    @Test
    void rejectsForeignOrigin() throws Exception {
        MockHttpServletRequest request = validPost();
        request.addHeader("Origin", "https://evil.example");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(request, response, (req, res) -> {
        });

        assertProblem(response, 403, "CSRF_REJECTED");
    }

    @Test
    void allowsConfiguredOrigin() throws Exception {
        MockHttpServletRequest request = validPost();
        request.addHeader("Origin", "http://localhost:5180");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean proceeded = new AtomicBoolean();

        filter().doFilter(request, response, (req, res) -> proceeded.set(true));

        assertThat(proceeded).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void leavesGetRequestsUntouched() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/read");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean proceeded = new AtomicBoolean();

        filter().doFilter(request, response, (req, res) -> proceeded.set(true));

        assertThat(proceeded).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void allowsRefererWhenOriginIsAbsent() throws Exception {
        MockHttpServletRequest request = validPost();
        request.addHeader("Referer", "https://auth.handsoffretail.com/account");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean proceeded = new AtomicBoolean();

        filter().doFilter(request, response, (req, res) -> proceeded.set(true));

        assertThat(proceeded).isTrue();
    }

    private CsrfHeaderFilter patternFilter() {
        HorAuthClientProperties properties = new HorAuthClientProperties();
        properties.setAllowedOrigins(List.of("https://*.handsoffretail.com", "http://localhost:*"));
        return new CsrfHeaderFilter(properties);
    }

    private boolean patternAllows(String origin) throws Exception {
        MockHttpServletRequest request = validPost();
        request.addHeader("Origin", origin);
        AtomicBoolean proceeded = new AtomicBoolean();
        patternFilter().doFilter(request, new MockHttpServletResponse(), (req, res) -> proceeded.set(true));
        return proceeded.get();
    }

    @Test
    void subdomainPatternAllowsAnySubdomainOverHttps() throws Exception {
        assertThat(patternAllows("https://auth.handsoffretail.com")).isTrue();
        assertThat(patternAllows("https://ingestion.handsoffretail.com")).isTrue();
        assertThat(patternAllows("https://a.b.handsoffretail.com")).isTrue();
        assertThat(patternAllows("https://APPS.HandsOffRetail.com")).isTrue();
    }

    @Test
    void subdomainPatternRejectsLookAlikesWrongSchemeAndPort() throws Exception {
        assertThat(patternAllows("https://evilhandsoffretail.com")).isFalse();
        assertThat(patternAllows("https://handsoffretail.com.evil.example")).isFalse();
        assertThat(patternAllows("https://handsoffretail.com")).isFalse();
        assertThat(patternAllows("http://auth.handsoffretail.com")).isFalse();
        assertThat(patternAllows("https://auth.handsoffretail.com:8443")).isFalse();
        assertThat(patternAllows("null")).isFalse();
    }

    @Test
    void anyPortPatternAllowsLocalhostOnEveryPortOnly() throws Exception {
        assertThat(patternAllows("http://localhost:5180")).isTrue();
        assertThat(patternAllows("http://localhost:3000")).isTrue();
        assertThat(patternAllows("http://localhost")).isTrue();
        assertThat(patternAllows("http://localhost.evil.example:5180")).isFalse();
        assertThat(patternAllows("http://127.0.0.1:5180")).isFalse();
    }

    @Test
    void invalidPatternFailsAtStartup() {
        HorAuthClientProperties properties = new HorAuthClientProperties();
        properties.setAllowedOrigins(List.of("https://auth.*.com"));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new CsrfHeaderFilter(properties))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private MockHttpServletRequest validPost() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/write");
        request.addHeader("X-HOR-Request", "1");
        return request;
    }

    private void assertProblem(MockHttpServletResponse response, int status, String code) throws Exception {
        assertThat(response.getStatus()).isEqualTo(status);
        assertThat(response.getContentType()).isEqualTo("application/problem+json;charset=UTF-8");
        assertThat(response.getContentAsString()).contains("\"code\":\"" + code + "\"");
    }
}
