package com.handsoff.hor.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.handsoff.hor.auth.bootstrap.BootstrapService;
import com.handsoff.hor.auth.token.RefreshTokens;
import com.handsoff.hor.auth.password.PasswordHasher;
import com.handsoff.hor.auth.support.TestDatabaseInitializer;
import com.handsoff.hor.auth.support.TestFlywayConfiguration;
import com.handsoff.hor.auth.support.AuthHttpClient;
import com.handsoff.hor.auth.support.MutableTestClock;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestFlywayConfiguration.class)
@ContextConfiguration(initializers = TestDatabaseInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AuthServiceApplicationTests {
    private static final String BOOTSTRAP_PASSWORD = java.util.UUID.randomUUID().toString() + "-test";
    private static final String STORE_USER_PASSWORD = java.util.UUID.randomUUID().toString() + "-test";
    private static final String STORE_USER_EMAIL = "session.user@test.local";

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private BootstrapService bootstrapService;

    @Autowired
    private MutableTestClock clock;

    @Autowired
    private RefreshTokens refreshTokens;

    @Autowired
    private PasswordHasher passwordHasher;

    @LocalServerPort
    private int port;

    @BeforeEach
    void resetTestClock() {
        clock.reset();
    }

    @DynamicPropertySource
    static void bootstrapCredentials(DynamicPropertyRegistry registry) {
        registry.add("hor.auth.bootstrap.admin.email", () -> "bootstrap.admin@test.local");
        registry.add("hor.auth.bootstrap.admin.password", () -> BOOTSTRAP_PASSWORD);
    }

    @Test
    void createsFirstAdminAndStoreWithAuditRowsAndIsIdempotent() {
        assertThat(jdbcClient.sql("SELECT count(*) FROM core.platform_admins").query(Long.class).single()).isEqualTo(1L);
        assertThat(jdbcClient.sql("SELECT count(*) FROM core.stores").query(Long.class).single()).isEqualTo(1L);
        assertThat(jdbcClient.sql("SELECT count(*) FROM core.audit_log").query(Long.class).single()).isEqualTo(2L);

        bootstrapService.initialize();

        assertThat(jdbcClient.sql("SELECT count(*) FROM core.platform_admins").query(Long.class).single()).isEqualTo(1L);
        assertThat(jdbcClient.sql("SELECT count(*) FROM core.stores").query(Long.class).single()).isEqualTo(1L);
        assertThat(jdbcClient.sql("SELECT count(*) FROM core.audit_log").query(Long.class).single()).isEqualTo(2L);
    }

    @Test
    void loginSetsCookieFlagsAndMeAuthenticatesFromCookies() throws Exception {
        AuthHttpClient client = new AuthHttpClient(port);
        HttpResponse<String> login = client.login("bootstrap.admin@test.local", BOOTSTRAP_PASSWORD);

        assertThat(login.statusCode()).isEqualTo(200);
        List<String> setCookies = login.headers().allValues("Set-Cookie");
        assertThat(setCookies.size()).isEqualTo(2);
        assertThat(client.cookieNames()).as("cookie metadata: " + String.join(" ", cookieAttributes(setCookies)))
                .containsExactlyInAnyOrder("hor_access", "hor_refresh");
        String cookieAttributes = String.join(" ", cookieAttributes(setCookies));
        assertThat(cookieAttributes).contains(
                "hor_access: Path=/;", "Max-Age=900", "Secure", "HttpOnly", "SameSite=Lax",
                "hor_refresh: Path=/api/auth", "Max-Age=43200");
        assertTrue(setCookies.stream().noneMatch(value -> value.contains("Domain=")),
                "Development cookies must remain host-only.");

        HttpResponse<String> me = client.get("/api/auth/me");
        assertThat(me.statusCode()).isEqualTo(200);
        assertThat(me.body()).contains("PLATFORM_ADMIN", "bootstrap.admin@test.local", "Platform Administrator");
    }

    @Test
    void tenWrongPasswordsLockAccountUntilClockAdvancesAndSuccessResetsCount() throws Exception {
        AuthHttpClient client = new AuthHttpClient(port);
        String wrongPassword = UUID.randomUUID().toString();
        for (int attempt = 1; attempt <= 10; attempt++) {
            HttpResponse<String> response = client.login("bootstrap.admin@test.local", wrongPassword);
            assertThat(response.statusCode()).isEqualTo(attempt == 10 ? 423 : 401);
            assertThat(response.body()).contains(attempt == 10 ? "ACCOUNT_LOCKED" : "BAD_CREDENTIALS");
        }
        assertThat(client.login("bootstrap.admin@test.local", BOOTSTRAP_PASSWORD).statusCode()).isEqualTo(423);
        clock.advance(Duration.ofMinutes(16));
        assertThat(client.login("bootstrap.admin@test.local", BOOTSTRAP_PASSWORD).statusCode()).isEqualTo(200);
        assertThat(jdbcClient.sql("SELECT failed_login_count FROM core.platform_admins")
                .query(Integer.class).single()).isZero();
    }

    @Test
    void aNewAdminLoginEndsTheEarlierSession() throws Exception {
        AuthHttpClient clientA = new AuthHttpClient(port);
        AuthHttpClient clientB = new AuthHttpClient(port);
        assertThat(clientA.login("bootstrap.admin@test.local", BOOTSTRAP_PASSWORD).statusCode()).isEqualTo(200);
        assertThat(clientB.login("bootstrap.admin@test.local", BOOTSTRAP_PASSWORD).statusCode()).isEqualTo(200);
        assertThat(clientB.cookieNames()).containsExactlyInAnyOrder("hor_access", "hor_refresh");
        boolean persisted = jdbcClient.sql("SELECT EXISTS (SELECT 1 FROM core.admin_refresh_tokens WHERE token_hash = :hash AND revoked_at IS NULL)")
                .param("hash", refreshTokens.hash(clientB.cookieValue("hor_refresh")))
                .query(Boolean.class).single();
        assertThat(persisted).isTrue();

        HttpResponse<String> oldSession = clientA.post("/api/auth/refresh");
        assertThat(oldSession.statusCode()).isEqualTo(401);
        assertThat(oldSession.body()).contains("SESSION_ENDED");
        HttpResponse<String> currentSession = clientB.post("/api/auth/refresh");
        assertThat(currentSession.statusCode()).as("refresh problem: " + currentSession.body()).isEqualTo(204);
    }

    @Test
    void reusingRotatedRefreshTokenRevokesItsForwardChain() throws Exception {
        AuthHttpClient client = new AuthHttpClient(port);
        assertThat(client.login("bootstrap.admin@test.local", BOOTSTRAP_PASSWORD).statusCode()).isEqualTo(200);
        Map<String, String> oldCookies = client.snapshotCookies();
        HttpResponse<String> rotated = client.post("/api/auth/refresh");
        assertThat(rotated.statusCode()).as("refresh problem: " + rotated.body()).isEqualTo(204);
        Map<String, String> currentCookies = client.snapshotCookies();

        client.restoreCookies(oldCookies);
        HttpResponse<String> reused = client.post("/api/auth/refresh");
        assertThat(reused.statusCode()).isEqualTo(401);
        assertThat(reused.body()).contains("SESSION_ENDED");
        client.restoreCookies(currentCookies);
        assertThat(client.post("/api/auth/refresh").statusCode()).isEqualTo(401);
    }

    @Test
    void logoutClearsCookiesAndEndsRefreshSession() throws Exception {
        AuthHttpClient client = new AuthHttpClient(port);
        assertThat(client.login("bootstrap.admin@test.local", BOOTSTRAP_PASSWORD).statusCode()).isEqualTo(200);

        assertThat(client.post("/api/auth/logout").statusCode()).isEqualTo(204);
        assertTrue(client.cookieHeader().isEmpty(), "Logout must remove cookies from the test client.");
        assertThat(client.post("/api/auth/refresh").statusCode()).isEqualTo(401);
    }

    @Test
    void storeUsersCanKeepMultipleActiveSessions() throws Exception {
        createTestStoreUser();
        AuthHttpClient clientA = new AuthHttpClient(port);
        AuthHttpClient clientB = new AuthHttpClient(port);
        assertThat(clientA.login(STORE_USER_EMAIL, STORE_USER_PASSWORD).statusCode()).isEqualTo(200);
        assertThat(clientB.login(STORE_USER_EMAIL, STORE_USER_PASSWORD).statusCode()).isEqualTo(200);

        assertThat(clientA.post("/api/auth/refresh").statusCode()).isEqualTo(204);
        assertThat(clientB.post("/api/auth/refresh").statusCode()).isEqualTo(204);
        assertThat(jdbcClient.sql("SELECT count(*) FROM core.user_refresh_tokens WHERE revoked_at IS NULL")
                .query(Long.class).single()).isEqualTo(2L);
    }

    @Test
    void writeRequestsRequireCsrfHeaderAndAllowedOrigin() throws Exception {
        AuthHttpClient client = new AuthHttpClient(port);

        HttpResponse<String> missingHeader = client.postWithoutCsrf("/api/auth/logout");
        assertThat(missingHeader.statusCode()).isEqualTo(403);
        assertThat(missingHeader.body()).contains("CSRF_REJECTED");
        HttpResponse<String> foreignOrigin = client.postWithOrigin("/api/auth/logout", "https://foreign.example");
        assertThat(foreignOrigin.statusCode()).isEqualTo(403);
        assertThat(foreignOrigin.body()).contains("CSRF_REJECTED");
    }

    @Test
    void expiredAccessCookieCanBeRefreshed() throws Exception {
        AuthHttpClient client = new AuthHttpClient(port);
        assertThat(client.login("bootstrap.admin@test.local", BOOTSTRAP_PASSWORD).statusCode()).isEqualTo(200);
        clock.advance(Duration.ofMinutes(16));

        HttpResponse<String> me = client.get("/api/auth/me");
        assertThat(me.statusCode()).isEqualTo(401);
        assertThat(me.body()).contains("TOKEN_EXPIRED");
        assertThat(client.post("/api/auth/refresh").statusCode()).isEqualTo(204);
    }

    @Test
    void concurrentAdminLoginsLeaveExactlyOneActiveSession() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<HttpResponse<String>> first = executor.submit(() ->
                    new AuthHttpClient(port).login("bootstrap.admin@test.local", BOOTSTRAP_PASSWORD));
            Future<HttpResponse<String>> second = executor.submit(() ->
                    new AuthHttpClient(port).login("bootstrap.admin@test.local", BOOTSTRAP_PASSWORD));
            assertThat(first.get().statusCode()).isEqualTo(200);
            assertThat(second.get().statusCode()).isEqualTo(200);
        } finally {
            executor.shutdownNow();
        }
        assertThat(jdbcClient.sql("SELECT count(*) FROM core.admin_refresh_tokens WHERE revoked_at IS NULL")
                .query(Long.class).single()).isEqualTo(1L);
    }

    private void createTestStoreUser() {
        java.time.Instant now = clock.instant();
        long adminId = jdbcClient.sql("SELECT admin_id FROM core.platform_admins ORDER BY admin_id LIMIT 1")
                .query(Long.class).single();
        jdbcClient.sql("INSERT INTO core.account_emails (email, account_kind) VALUES (:email, 'STORE_USER')")
                .param("email", STORE_USER_EMAIL)
                .update();
        jdbcClient.sql("""
                INSERT INTO core.store_users
                    (user_type, full_name, email, password_hash, password_changed_at, created_by, created_at, updated_at)
                VALUES ('CLIENT', 'Session Test User', :email, :passwordHash, :now, :adminId, :now, :now)
                """)
                .param("email", STORE_USER_EMAIL)
                .param("passwordHash", passwordHasher.hash(STORE_USER_PASSWORD))
                .param("now", now.atOffset(java.time.ZoneOffset.UTC))
                .param("adminId", adminId)
                .update();
    }

    private List<String> cookieAttributes(List<String> cookies) {
        return cookies.stream().map(cookie -> {
            int valueEnd = cookie.indexOf(';');
            int nameEnd = cookie.indexOf('=');
            if (nameEnd < 0) {
                return cookie.replaceAll("[A-Za-z0-9._~-]{20,}", "<redacted>");
            }
            String name = cookie.substring(0, nameEnd);
            return name + ": " + (valueEnd < 0 ? "" : cookie.substring(valueEnd + 1).strip());
        }).toList();
    }
}
