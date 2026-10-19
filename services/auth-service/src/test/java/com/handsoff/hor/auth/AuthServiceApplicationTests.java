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
import org.flywaydb.core.Flyway;
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
    private static final String MANAGED_ADMIN_PASSWORD = UUID.randomUUID().toString() + "-test";

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

    @Autowired
    private Flyway flyway;

    @LocalServerPort
    private int port;

    @BeforeEach
    void resetTestDatabase() {
        clock.reset();
        jdbcClient.sql("DROP SCHEMA IF EXISTS core CASCADE").update();
        jdbcClient.sql("DROP EXTENSION IF EXISTS citext CASCADE").update();
        flyway.clean();
        flyway.migrate();
        bootstrapService.initialize();
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
    void adminCreatesStaffAndClientWithFixedTypeAndOptionalPatchRemoval() throws Exception {
        AuthHttpClient actor = signedInBootstrapAdmin();
        String staffPassword = UUID.randomUUID().toString() + "-test";
        String clientPassword = UUID.randomUUID().toString() + "-test";
        HttpResponse<String> staff = createUser(actor, "STAFF", "staff.user@test.local", "Staff User",
                staffPassword, "555 555 1234", "Office");
        HttpResponse<String> client = createUser(actor, "CLIENT", "client.user@test.local", "Client User",
                clientPassword, null, "Client Address");
        assertThat(staff.statusCode()).isEqualTo(201);
        assertThat(staff.body()).contains("HOR_STAFF");
        assertThat(client.statusCode()).isEqualTo(201);
        assertThat(client.body()).contains("CLIENT_USER");

        long staffId = storeUserId("staff.user@test.local");
        HttpResponse<String> fixedType = actor.send("PATCH", "/api/auth/admin/users/" + staffId,
                "{\"userType\":\"CLIENT\"}", true, null);
        assertThat(fixedType.statusCode()).isEqualTo(422);
        assertThat(fixedType.body()).contains("USER_TYPE_FIXED");

        HttpResponse<String> removedOptionalFields = actor.send("PATCH", "/api/auth/admin/users/" + staffId,
                "{\"phoneNumber\":\"\",\"address\":\"\"}", true, null);
        assertThat(removedOptionalFields.statusCode()).isEqualTo(200);
        assertThat(removedOptionalFields.body()).doesNotContain("phoneNumber", "address");
        assertThat(jdbcClient.sql("SELECT phone_number IS NULL AND address IS NULL FROM core.store_users WHERE user_id = :id")
                .param("id", staffId).query(Boolean.class).single()).isTrue();

        HttpResponse<String> mixedStatus = actor.send("PATCH", "/api/auth/admin/users/" + staffId,
                "{\"status\":\"DISABLED\",\"address\":\"new\"}", true, null);
        assertThat(mixedStatus.statusCode()).isEqualTo(422);
        assertThat(mixedStatus.body()).contains("STATUS_CHANGE_ALONE");
    }

    @Test
    void emailUniquenessIsSharedBetweenAdminsAndStoreUsers() throws Exception {
        AuthHttpClient actor = signedInBootstrapAdmin();
        String password = UUID.randomUUID().toString() + "-test";
        HttpResponse<String> conflict = createUser(actor, "CLIENT", "bootstrap.admin@test.local", "Duplicate",
                password, null, null);
        assertThat(conflict.statusCode()).isEqualTo(409);
        assertThat(conflict.body()).contains("EMAIL_TAKEN");
    }

    @Test
    void disablingAndResettingUsersRevokeSessions() throws Exception {
        AuthHttpClient actor = signedInBootstrapAdmin();
        String email = "disabled.user@test.local";
        String password = UUID.randomUUID().toString() + "-test";
        assertThat(createUser(actor, "STAFF", email, "Disabled User", password, null, null).statusCode()).isEqualTo(201);
        long id = storeUserId(email);
        AuthHttpClient user = new AuthHttpClient(port);
        assertThat(user.login(email, password).statusCode()).isEqualTo(200);

        assertThat(actor.send("PATCH", "/api/auth/admin/users/" + id, "{\"status\":\"DISABLED\"}", true, null)
                .statusCode()).isEqualTo(200);
        assertThat(user.post("/api/auth/refresh").statusCode()).isEqualTo(401);
        assertThat(actor.send("PATCH", "/api/auth/admin/users/" + id, "{\"status\":\"ACTIVE\"}", true, null)
                .statusCode()).isEqualTo(200);
        assertThat(user.login(email, password).statusCode()).isEqualTo(200);

        String replacementPassword = UUID.randomUUID().toString() + "-test";
        assertThat(actor.send("POST", "/api/auth/admin/users/" + id + "/password-reset",
                "{\"newPassword\":\"" + replacementPassword + "\"}", true, null).statusCode()).isEqualTo(204);
        assertThat(user.post("/api/auth/refresh").statusCode()).isEqualTo(401);
        assertThat(new AuthHttpClient(port).login(email, password).statusCode()).isEqualTo(401);
        assertThat(new AuthHttpClient(port).login(email, replacementPassword).statusCode()).isEqualTo(200);
    }

    @Test
    void unlockingAnUnlockedUserStillCreatesAuditRow() throws Exception {
        AuthHttpClient actor = signedInBootstrapAdmin();
        String email = "unlock.user@test.local";
        assertThat(createUser(actor, "CLIENT", email, "Unlock User", UUID.randomUUID().toString() + "-test",
                null, null).statusCode()).isEqualTo(201);
        long id = storeUserId(email);
        assertThat(jdbcClient.sql("SELECT count(*) FROM core.audit_log WHERE action = 'USER_UNLOCKED' AND target_id = :id")
                .param("id", id).query(Long.class).single()).isZero();
        assertThat(actor.send("POST", "/api/auth/admin/users/" + id + "/unlock", null, true, null)
                .statusCode()).isEqualTo(204);
        assertThat(jdbcClient.sql("SELECT count(*) FROM core.audit_log WHERE action = 'USER_UNLOCKED' AND target_id = :id")
                .param("id", id).query(Long.class).single()).isEqualTo(1L);
    }

    @Test
    void adminCreateSearchCaseInsensitiveEmailUniquenessAndPagination() throws Exception {
        AuthHttpClient actor = signedInBootstrapAdmin();
        String email = "managed.admin@test.local";
        HttpResponse<String> created = createAdmin(actor, email, "Managed Admin", MANAGED_ADMIN_PASSWORD, null);
        assertThat(created.statusCode()).isEqualTo(201);

        HttpResponse<String> found = actor.get("/api/auth/admin/platform-admins?q=AGED.ADMIN&limit=1&offset=0");
        assertThat(found.statusCode()).isEqualTo(200);
        assertThat(found.body()).contains("managed.admin@test.local", "Managed Admin", "\"total\":1");

        HttpResponse<String> duplicate = createAdmin(actor, email.toUpperCase(java.util.Locale.ROOT),
                "Duplicate Admin", MANAGED_ADMIN_PASSWORD, null);
        assertThat(duplicate.statusCode()).isEqualTo(409);
        assertThat(duplicate.body()).contains("EMAIL_TAKEN");

        HttpResponse<String> emptyPage = actor.get("/api/auth/admin/platform-admins?limit=0");
        assertThat(emptyPage.statusCode()).isEqualTo(200);
        assertThat(emptyPage.body()).contains("\"items\":[]", "\"limit\":0");
        HttpResponse<String> invalidLimit = actor.get("/api/auth/admin/platform-admins?limit=-1");
        assertThat(invalidLimit.statusCode()).isEqualTo(400);
    }

    @Test
    void adminPatchChangesEmailAndEmptyPhoneRemovesValue() throws Exception {
        AuthHttpClient actor = signedInBootstrapAdmin();
        String email = "editable.admin@test.local";
        assertThat(createAdmin(actor, email, "Editable Admin", MANAGED_ADMIN_PASSWORD, "555 555 5555").statusCode())
                .isEqualTo(201);
        long id = adminId(email);

        HttpResponse<String> updated = actor.send("PATCH", "/api/auth/admin/platform-admins/" + id,
                "{\"email\":\"renamed.admin@test.local\",\"fullName\":\"Renamed Admin\",\"phoneNumber\":\"\"}",
                true, null);
        assertThat(updated.statusCode()).isEqualTo(200);
        assertThat(updated.body()).contains("renamed.admin@test.local", "Renamed Admin");
        assertThat(updated.body()).doesNotContain("phoneNumber");
        assertThat(jdbcClient.sql("SELECT count(*) FROM core.account_emails WHERE email = :email")
                .param("email", email).query(Long.class).single()).isZero();
    }

    @Test
    void adminStatusMustBeAloneAndDisablingEndsTargetSessions() throws Exception {
        AuthHttpClient actor = signedInBootstrapAdmin();
        String email = "disable.target@test.local";
        assertThat(createAdmin(actor, email, "Disable Target", MANAGED_ADMIN_PASSWORD, null).statusCode()).isEqualTo(201);
        long id = adminId(email);
        AuthHttpClient target = new AuthHttpClient(port);
        assertThat(target.login(email, MANAGED_ADMIN_PASSWORD).statusCode()).isEqualTo(200);

        HttpResponse<String> combined = actor.send("PATCH", "/api/auth/admin/platform-admins/" + id,
                "{\"status\":\"DISABLED\",\"fullName\":\"Other Name\"}", true, null);
        assertThat(combined.statusCode()).isEqualTo(422);
        assertThat(combined.body()).contains("STATUS_CHANGE_ALONE");

        HttpResponse<String> disabled = actor.send("PATCH", "/api/auth/admin/platform-admins/" + id,
                "{\"status\":\"DISABLED\"}", true, null);
        assertThat(disabled.statusCode()).isEqualTo(200);
        assertThat(target.post("/api/auth/refresh").statusCode()).isEqualTo(401);
        assertThat(jdbcClient.sql("SELECT count(*) FROM core.audit_log WHERE action = 'ADMIN_DISABLED' AND target_id = :id")
                .param("id", id).query(Long.class).single()).isEqualTo(1L);
    }

    @Test
    void adminCannotDisableSelfOrLastActiveAdmin() throws Exception {
        AuthHttpClient actor = signedInBootstrapAdmin();
        long bootstrapId = adminId("bootstrap.admin@test.local");
        HttpResponse<String> selfDisable = actor.send("PATCH", "/api/auth/admin/platform-admins/" + bootstrapId,
                "{\"status\":\"DISABLED\"}", true, null);
        assertThat(selfDisable.statusCode()).isEqualTo(422);
        assertThat(selfDisable.body()).contains("CANNOT_DISABLE_SELF");

        String secondEmail = "second.admin@test.local";
        String secondPassword = UUID.randomUUID().toString() + "-test";
        assertThat(createAdmin(actor, secondEmail, "Second Admin", secondPassword, null).statusCode()).isEqualTo(201);
        long secondId = adminId(secondEmail);
        AuthHttpClient second = new AuthHttpClient(port);
        assertThat(second.login(secondEmail, secondPassword).statusCode()).isEqualTo(200);
        jdbcClient.sql("UPDATE core.platform_admins SET status = 'DISABLED' WHERE admin_id = :id")
                .param("id", secondId).update();

        HttpResponse<String> lastAdmin = second.send("PATCH", "/api/auth/admin/platform-admins/" + bootstrapId,
                "{\"status\":\"DISABLED\"}", true, null);
        assertThat(lastAdmin.statusCode()).isEqualTo(422);
        assertThat(lastAdmin.body()).contains("LAST_ACTIVE_ADMIN");
    }

    @Test
    void passwordResetMayTargetTheCurrentAdminAndUnlockAlwaysAudits() throws Exception {
        AuthHttpClient actor = signedInBootstrapAdmin();
        long id = adminId("bootstrap.admin@test.local");
        String replacementPassword = UUID.randomUUID().toString() + "-test";
        HttpResponse<String> reset = actor.send("POST", "/api/auth/admin/platform-admins/" + id + "/password-reset",
                "{\"newPassword\":\"" + replacementPassword + "\"}", true, null);
        assertThat(reset.statusCode()).isEqualTo(204);
        assertThat(actor.post("/api/auth/refresh").statusCode()).isEqualTo(401);
        AuthHttpClient reauthenticated = new AuthHttpClient(port);
        assertThat(reauthenticated.login("bootstrap.admin@test.local", replacementPassword).statusCode()).isEqualTo(200);

        long before = jdbcClient.sql("SELECT count(*) FROM core.audit_log WHERE action = 'ADMIN_UNLOCKED' AND target_id = :id")
                .param("id", id).query(Long.class).single();
        assertThat(reauthenticated.send("POST", "/api/auth/admin/platform-admins/" + id + "/unlock", null, true, null)
                .statusCode()).isEqualTo(204);
        assertThat(reauthenticated.send("POST", "/api/auth/admin/platform-admins/" + id + "/unlock", null, true, null)
                .statusCode()).isEqualTo(204);
        long after = jdbcClient.sql("SELECT count(*) FROM core.audit_log WHERE action = 'ADMIN_UNLOCKED' AND target_id = :id")
                .param("id", id).query(Long.class).single();
        assertThat(after - before).isEqualTo(2L);
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

    private AuthHttpClient signedInBootstrapAdmin() throws Exception {
        AuthHttpClient client = new AuthHttpClient(port);
        assertThat(client.login("bootstrap.admin@test.local", BOOTSTRAP_PASSWORD).statusCode()).isEqualTo(200);
        return client;
    }

    private HttpResponse<String> createAdmin(AuthHttpClient actor, String email, String fullName,
            String password, String phoneNumber) throws Exception {
        String phone = phoneNumber == null ? "" : ",\"phoneNumber\":\"" + phoneNumber + "\"";
        String body = "{\"email\":\"" + email + "\",\"fullName\":\"" + fullName
                + "\",\"password\":\"" + password + "\"" + phone + "}";
        return actor.send("POST", "/api/auth/admin/platform-admins", body, true, null);
    }

    private long adminId(String email) {
        return jdbcClient.sql("SELECT admin_id FROM core.platform_admins WHERE email = :email")
                .param("email", email).query(Long.class).single();
    }

    private HttpResponse<String> createUser(AuthHttpClient actor, String userType, String email,
            String fullName, String password, String phoneNumber, String address) throws Exception {
        String phone = phoneNumber == null ? "" : ",\"phoneNumber\":\"" + phoneNumber + "\"";
        String userAddress = address == null ? "" : ",\"address\":\"" + address + "\"";
        String body = "{\"userType\":\"" + userType + "\",\"email\":\"" + email
                + "\",\"fullName\":\"" + fullName + "\",\"password\":\"" + password
                + "\"" + phone + userAddress + "}";
        return actor.send("POST", "/api/auth/admin/users", body, true, null);
    }

    private long storeUserId(String email) {
        return jdbcClient.sql("SELECT user_id FROM core.store_users WHERE email = :email")
                .param("email", email).query(Long.class).single();
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
