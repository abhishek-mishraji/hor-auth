package com.handsoff.hor.auth.client;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

public final class ProblemResponses {
    private ProblemResponses() {
    }

    public static AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, exception) -> {
            String message = exception.getMessage();
            boolean expired = message != null && message.toLowerCase(Locale.ROOT).contains("expired");
            write(response,
                    expired ? CommonErrorCode.TOKEN_EXPIRED : CommonErrorCode.UNAUTHENTICATED,
                    expired ? "The access token has expired." : "Authentication is required.");
        };
    }

    public static AccessDeniedHandler accessDeniedHandler() {
        return (request, response, exception) -> write(
                response, CommonErrorCode.FORBIDDEN, "Access is forbidden.");
    }

    static void write(HttpServletResponse response, ErrorCode errorCode, String detail) throws IOException {
        int status = errorCode.status().value();
        String body = "{\"type\":\"about:blank\",\"title\":\"" + title(status)
                + "\",\"status\":" + status + ",\"detail\":\"" + detail
                + "\",\"code\":\"" + errorCode.code() + "\"}";
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
    }

    private static String title(int status) {
        return switch (status) {
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            default -> "Error";
        };
    }
}
