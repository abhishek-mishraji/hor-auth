package com.handsoffretail.auth.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;

class ProblemResponsesTest {
    @Test
    void writesUnauthenticatedProblemBody() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        ProblemResponses.authenticationEntryPoint().commence(
                new MockHttpServletRequest(), response, new BadCredentialsException("credentials required"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).isEqualTo("application/problem+json;charset=UTF-8");
        assertThat(response.getContentAsString()).contains("\"code\":\"UNAUTHENTICATED\"");
    }
}
