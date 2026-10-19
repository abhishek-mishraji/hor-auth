package com.handsoff.hor.auth.session;

import com.handsoff.hor.auth.account.AccountType;
import com.handsoff.hor.auth.client.RequestLoggingFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class SessionController {
    private final SessionService sessionService;
    private final AuthCookies cookies;

    public SessionController(SessionService sessionService, AuthCookies cookies) {
        this.sessionService = sessionService;
        this.cookies = cookies;
    }

    @PostMapping("/login")
    public ResponseEntity<Me> login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        SessionResult result = sessionService.login(request.email(), request.password());
        servletRequest.setAttribute(RequestLoggingFilter.ACCOUNT_ATTRIBUTE,
                result.me().accountType() + ":" + result.me().id());
        HttpHeaders headers = new HttpHeaders();
        cookies.write(headers, result.accessToken(), result.refreshToken());
        return ResponseEntity.ok().headers(headers).body(result.me());
    }

    @PostMapping("/refresh")
    public ResponseEntity<Void> refresh(
            @CookieValue(name = "hor_refresh", required = false) String refreshToken,
            HttpServletResponse response,
            HttpServletRequest servletRequest) {
        try {
            SessionResult result = sessionService.refresh(refreshToken);
            servletRequest.setAttribute(RequestLoggingFilter.ACCOUNT_ATTRIBUTE,
                    result.me().accountType() + ":" + result.me().id());
            HttpHeaders headers = new HttpHeaders();
            cookies.write(headers, result.accessToken(), result.refreshToken());
            return ResponseEntity.noContent().headers(headers).build();
        } catch (SessionEndedException exception) {
            cookies.clear(response);
            throw exception;
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = "hor_refresh", required = false) String refreshToken,
            HttpServletResponse response) {
        sessionService.logout(refreshToken);
        cookies.clear(response);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public Me me(JwtAuthenticationToken authentication) {
        return sessionService.me(accountType(authentication), accountId(authentication));
    }

    @PostMapping("/me/password")
    public ResponseEntity<Void> changePassword(
            @Valid @RequestBody PasswordChangeRequest request,
            @CookieValue(name = "hor_refresh", required = false) String refreshToken,
            JwtAuthenticationToken authentication,
            HttpServletResponse response) {
        SessionResult result = sessionService.changePassword(accountType(authentication), accountId(authentication),
                request.currentPassword(), request.newPassword(), refreshToken);
        HttpHeaders headers = new HttpHeaders();
        cookies.write(headers, result.accessToken(), result.refreshToken());
        return ResponseEntity.noContent().headers(headers).build();
    }

    private AccountType accountType(JwtAuthenticationToken authentication) {
        return AccountType.valueOf(authentication.getToken().getClaimAsString("typ"));
    }

    private long accountId(JwtAuthenticationToken authentication) {
        return Long.parseLong(authentication.getToken().getSubject());
    }
}
