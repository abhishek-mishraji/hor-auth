package com.handsoffretail.auth.client;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.web.filter.OncePerRequestFilter;

public final class CsrfHeaderFilter extends OncePerRequestFilter {
    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");
    private final Set<String> allowedOrigins;

    public CsrfHeaderFilter(HorAuthClientProperties properties) {
        allowedOrigins = properties.getAllowedOrigins().stream()
                .map(this::normalizedOrigin)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!WRITE_METHODS.contains(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }
        if (!"1".equals(request.getHeader("X-HOR-Request")) || !isAllowedRequestOrigin(request)) {
            ProblemResponses.write(response, CommonErrorCode.CSRF_REJECTED, "Request origin could not be verified.");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean isAllowedRequestOrigin(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (origin != null) {
            return allowedOrigins.contains(normalizedOrigin(origin));
        }
        String referer = request.getHeader("Referer");
        return referer != null && allowedOrigins.contains(normalizedOrigin(referer));
    }

    private String normalizedOrigin(String value) {
        try {
            URI uri = new URI(value);
            if (uri.getScheme() == null || uri.getHost() == null || uri.getUserInfo() != null) {
                return "";
            }
            return new URI(uri.getScheme().toLowerCase(), null, uri.getHost().toLowerCase(), uri.getPort(), null, null, null)
                    .toString();
        } catch (URISyntaxException exception) {
            return "";
        }
    }
}
