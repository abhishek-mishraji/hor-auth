package com.handsoffretail.auth.client;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rejects write requests that lack the X-HOR-Request header or come from an origin that is not allowed.
 *
 * <p>Allowed-origin entries are either exact ({@code https://auth.handsoffretail.com}) or patterns:
 * <ul>
 *   <li>{@code https://*.handsoffretail.com}: any subdomain (not the bare domain), same scheme, default port;</li>
 *   <li>{@code http://localhost:*}: that host on any port;</li>
 *   <li>both combined: {@code https://*.example.com:*}.</li>
 * </ul>
 */
public final class CsrfHeaderFilter extends OncePerRequestFilter {
    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");
    private final Set<String> allowedOrigins;
    private final List<OriginPattern> allowedPatterns;

    public CsrfHeaderFilter(HorAuthClientProperties properties) {
        allowedOrigins = properties.getAllowedOrigins().stream()
                .map(String::trim)
                .filter(entry -> !entry.contains("*"))
                .map(this::normalizedOrigin)
                .filter(origin -> !origin.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        allowedPatterns = properties.getAllowedOrigins().stream()
                .map(String::trim)
                .filter(entry -> entry.contains("*"))
                .map(OriginPattern::parse)
                .toList();
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
            return isAllowed(origin);
        }
        String referer = request.getHeader("Referer");
        return referer != null && isAllowed(referer);
    }

    private boolean isAllowed(String value) {
        String normalized = normalizedOrigin(value);
        if (normalized.isEmpty()) {
            return false;
        }
        if (allowedOrigins.contains(normalized)) {
            return true;
        }
        URI uri = URI.create(normalized);
        return allowedPatterns.stream().anyMatch(pattern -> pattern.matches(uri));
    }

    /** One wildcard entry: scheme://[*.]host[:port|:*]. */
    private record OriginPattern(String scheme, String host, boolean anySubdomain, int port, boolean anyPort) {
        private static final Pattern SYNTAX =
                Pattern.compile("^([a-zA-Z][a-zA-Z0-9+.-]*)://(\\*\\.)?([a-zA-Z0-9.-]+)(?::(\\*|\\d{1,5}))?/?$");

        static OriginPattern parse(String entry) {
            Matcher matcher = SYNTAX.matcher(entry);
            if (!matcher.matches()) {
                throw new IllegalArgumentException("Invalid allowed-origin pattern: " + entry
                        + " (use e.g. https://*.example.com or http://localhost:*)");
            }
            String port = matcher.group(4);
            return new OriginPattern(
                    matcher.group(1).toLowerCase(Locale.ROOT),
                    matcher.group(3).toLowerCase(Locale.ROOT),
                    matcher.group(2) != null,
                    port == null || "*".equals(port) ? -1 : Integer.parseInt(port),
                    "*".equals(port));
        }

        boolean matches(URI origin) {
            if (!scheme.equals(origin.getScheme())) {
                return false;
            }
            String originHost = origin.getHost();
            boolean hostMatches = anySubdomain ? originHost.endsWith("." + host) : originHost.equals(host);
            return hostMatches && (anyPort || origin.getPort() == port);
        }
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
