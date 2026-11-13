package com.handsoffretail.auth.client;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

public final class RequestLoggingFilter extends OncePerRequestFilter {
    public static final String ACCOUNT_ATTRIBUTE = "hor.auth.account";
    private static final Logger LOGGER = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long startedAt = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMillis = (System.nanoTime() - startedAt) / 1_000_000;
            LOGGER.info("{} {} {} {}ms {}", request.getMethod(), request.getRequestURI(), response.getStatus(),
                    durationMillis, accountIdentifier(request));
        }
    }

    private String accountIdentifier(HttpServletRequest request) {
        Object requestAccount = request.getAttribute(ACCOUNT_ATTRIBUTE);
        if (requestAccount instanceof String accountIdentifier) {
            return accountIdentifier;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return "-";
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof Jwt jwt) {
            String type = jwt.getClaimAsString("typ");
            String id = jwt.getSubject();
            if (("ADMIN".equals(type) || "STORE_USER".equals(type)) && id != null) {
                return type + ":" + id;
            }
        }
        if (principal instanceof HorPrincipal horPrincipal) {
            return horPrincipal.accountType() + ":" + horPrincipal.id();
        }
        return "-";
    }
}
