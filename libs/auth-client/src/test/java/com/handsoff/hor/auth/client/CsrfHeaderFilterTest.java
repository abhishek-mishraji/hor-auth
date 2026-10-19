package com.handsoff.hor.auth.client;

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
