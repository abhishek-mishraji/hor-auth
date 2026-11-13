package com.handsoffretail.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.handsoffretail.auth.bootstrap.BootstrapService;
import com.handsoffretail.auth.token.RefreshTokens;
import com.handsoffretail.auth.password.PasswordHasher;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.handsoffretail.auth.support.TestDatabaseInitializer;
import com.handsoffretail.auth.support.TestFlywayConfiguration;
import com.handsoffretail.auth.support.AuthHttpClient;
import com.handsoffretail.auth.support.MutableTestClock;
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
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.context.annotation.Import;
import org.flywaydb.core.Flyway;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(OutputCaptureExtension.class)
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
    void jwtClaimsContainOnlyTheRequiredRoleAndActiveStoreAccess() throws Exception {
        AuthHttpClient actor = signedInBootstrapAdmin();
        JWTClaimsSet adminClaims = accessClaims(actor);
        assertThat(adminClaims.getBooleanClaim("all_stores")).isTrue();
        assertThat(adminClaims.getAudience()).isEmpty();
        assertThat(adminClaims.getClaims()).doesNotContainKeys("email", "phoneNumber");
        HttpResponse<String> jwks = actor.get("/api/auth/.well-known/jwks.json");
        assertThat(jwks.statusCode()).isEqualTo(200);
        assertThat(jwks.body()).contains("\"kid\"", "\"use\":\"sig\"", "\"alg\":\"RS256\"");
        assertThat(jwks.headers().firstValue("Cache-Control").orElse("")).contains("public", "max-age=3600");

        String code = "CL_" + UUID.randomUUID();
        createStore(actor, code, "Claim Store", null, null, "US", "IN", "47872");
        long storeId = storeId(code);
        String email = "claims.user@test.local";
        String password = UUID.randomUUID().toString() + "-test";
        createUser(actor, "CLIENT", email, "Claims User", password, null, null);
        long userId = storeUserId(email);
        actor.send("PUT", "/api/auth/admin/stores/" + storeId + "/members/" + userId,
                "{\"storeRole\":\"PARTNER\"}", true, null);
        AuthHttpClient user = new AuthHttpClient(port);
        assertThat(user.login(email, password).statusCode()).isEqualTo(200);
        JWTClaimsSet userClaims = accessClaims(user);
        assertThat(userClaims.getStringClaim("role")).isEqualTo("CLIENT_USER");
        assertThat(userClaims.getListClaim("stores")).containsExactly(storeId);
        assertThat(userClaims.getClaims()).doesNotContainKeys("email", "all_stores");

        assertThat(actor.send("PATCH", "/api/auth/admin/stores/" + storeId,
                "{\"status\":\"INACTIVE\"}", true, null).statusCode()).isEqualTo(200);
        assertThat(user.post("/api/auth/refresh").statusCode()).isEqualTo(204);
        assertThat(accessClaims(user).getListClaim("stores")).isEmpty();
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
    void ownPasswordChangeChecksCurrentPasswordAndRevokesOtherSessions() throws Exception {
        AuthHttpClient actor = signedInBootstrapAdmin();
        String email = "change.password@test.local";
        String currentPassword = UUID.randomUUID().toString() + "-test";
        createUser(actor, "CLIENT", email, "Password Change User", currentPassword, null, null);
        AuthHttpClient clientA = new AuthHttpClient(port);
        AuthHttpClient clientB = new AuthHttpClient(port);
        assertThat(clientA.login(email, currentPassword).statusCode()).isEqualTo(200);
        assertThat(clientB.login(email, currentPassword).statusCode()).isEqualTo(200);
        long auditCount = jdbcClient.sql("SELECT count(*) FROM core.audit_log").query(Long.class).single();
        String replacementPassword = UUID.randomUUID().toString() + "-test";
        HttpResponse<String> wrongCurrent = clientA.send("POST", "/api/auth/me/password",
                "{\"currentPassword\":\"" + UUID.randomUUID() + "\",\"newPassword\":\"" + replacementPassword + "\"}",
                true, null);
        assertThat(wrongCurrent.statusCode()).isEqualTo(422);
        assertThat(wrongCurrent.body()).contains("WRONG_PASSWORD");

        HttpResponse<String> changed = clientA.send("POST", "/api/auth/me/password",
                "{\"currentPassword\":\"" + currentPassword + "\",\"newPassword\":\"" + replacementPassword + "\"}",
                true, null);
        assertThat(changed.statusCode()).isEqualTo(204);
        assertThat(clientA.cookieNames()).containsExactlyInAnyOrder("hor_access", "hor_refresh");
        boolean activeRefreshToken = jdbcClient.sql("SELECT EXISTS (SELECT 1 FROM core.user_refresh_tokens WHERE token_hash = :hash AND revoked_at IS NULL)")
                .param("hash", refreshTokens.hash(clientA.cookieValue("hor_refresh")))
                .query(Boolean.class).single();
        assertThat(activeRefreshToken).isTrue();
        HttpResponse<String> ownRefresh = clientA.post("/api/auth/refresh");
        assertThat(ownRefresh.statusCode()).as("refresh problem: " + ownRefresh.body()).isEqualTo(204);
        assertThat(clientB.post("/api/auth/refresh").statusCode()).isEqualTo(401);
        assertThat(new AuthHttpClient(port).login(email, currentPassword).statusCode()).isEqualTo(401);
        assertThat(new AuthHttpClient(port).login(email, replacementPassword).statusCode()).isEqualTo(200);
        assertThat(jdbcClient.sql("SELECT count(*) FROM core.audit_log").query(Long.class).single()).isEqualTo(auditCount);
    }

    @Test
    void storeUsersAreForbiddenFromEveryAdminRoute() throws Exception {
        AuthHttpClient actor = signedInBootstrapAdmin();
        String email = "forbidden.user@test.local";
        String password = UUID.randomUUID().toString() + "-test";
        createUser(actor, "CLIENT", email, "Forbidden User", password, null, null);
        AuthHttpClient user = new AuthHttpClient(port);
        assertThat(user.login(email, password).statusCode()).isEqualTo(200);
        for (String path : List.of("/api/auth/admin/platform-admins", "/api/auth/admin/users",
                "/api/auth/admin/stores", "/api/auth/admin/audit-log",
                "/api/auth/admin/stores/1/members")) {
            HttpResponse<String> response = user.get(path);
            assertThat(response.statusCode()).isEqualTo(403);
            assertThat(response.body()).contains("FORBIDDEN");
        }
    }

    @Test
    void startupRefusesAnEmptyDatabaseWithoutBootstrapCredentials() {
        jdbcClient.sql("DROP SCHEMA IF EXISTS core CASCADE").update();
        jdbcClient.sql("DROP EXTENSION IF EXISTS citext CASCADE").update();
        flyway.clean();
        flyway.migrate();
        boolean bootstrapRefused = false;
        SpringApplication application = new SpringApplication(AuthServiceApplication.class);
        application.setWebApplicationType(WebApplicationType.SERVLET);
        try (var context = application.run("--spring.profiles.active=test", "--server.port=0",
                "--hor.auth.bootstrap.admin.email=", "--hor.auth.bootstrap.admin.password=")) {
            assertThat(context.isActive()).isTrue();
        } catch (RuntimeException exception) {
            bootstrapRefused = hasCauseMessage(exception,
                    "Bootstrap admin email and password are required when platform_admins is empty.");
        }
        assertTrue(bootstrapRefused, "Application must refuse first start without bootstrap credentials.");
    }

    @Test
    void authenticationAndSessionLogsExcludePasswordsHashesTokensAndCookies(CapturedOutput capturedOutput)
            throws Exception {
        AuthHttpClient actor = new AuthHttpClient(port);
        String password = BOOTSTRAP_PASSWORD;
        String replacementPassword = UUID.randomUUID().toString() + "-test";
        String originalHash = jdbcClient.sql("SELECT password_hash FROM core.platform_admins WHERE email = :email")
                .param("email", "bootstrap.admin@test.local").query(String.class).single();
        assertThat(actor.login("bootstrap.admin@test.local", password).statusCode()).isEqualTo(200);
        Map<String, String> loginCookies = actor.snapshotCookies();
        assertThat(actor.post("/api/auth/refresh").statusCode()).isEqualTo(204);
        Map<String, String> refreshCookies = actor.snapshotCookies();
        long adminId = adminId("bootstrap.admin@test.local");
        assertThat(actor.send("POST", "/api/auth/admin/platform-admins/" + adminId + "/password-reset",
                "{\"newPassword\":\"" + replacementPassword + "\"}", true, null).statusCode()).isEqualTo(204);
        String resetHash = jdbcClient.sql("SELECT password_hash FROM core.platform_admins WHERE admin_id = :id")
                .param("id", adminId).query(String.class).single();
        java.util.ArrayList<String> sensitiveValues = new java.util.ArrayList<>();
        sensitiveValues.add(password);
        sensitiveValues.add(replacementPassword);
        sensitiveValues.add(originalHash);
        sensitiveValues.add(resetHash);
        sensitiveValues.addAll(loginCookies.values());
        sensitiveValues.addAll(refreshCookies.values());
        String logs = capturedOutput.toString();
        assertTrue(sensitiveValues.stream().noneMatch(logs::contains),
                "Captured logs must not include credentials, hashes, tokens, or cookie values.");
        assertTrue(logs.contains("ADMIN:" + adminId), "Successful login and refresh logs should identify the account by id.");
    }

    @Test
    void auditFiltersUseInclusiveFromExclusiveToAndResolveTargetLabels() throws Exception {
        AuthHttpClient actor = signedInBootstrapAdmin();
        String email = "audit.user@test.local";
        createUser(actor, "CLIENT", email, "Audit User", UUID.randomUUID().toString() + "-test", null, null);
        String code = "AUD_" + UUID.randomUUID();
        createStore(actor, code, "Audit Store", null, null, "US", "IN", "47872");
        long storeId = storeId(code);
        long userId = storeUserId(email);
        assertThat(actor.send("PUT", "/api/auth/admin/stores/" + storeId + "/members/" + userId,
                "{\"storeRole\":\"PARTNER\"}", true, null).statusCode()).isEqualTo(201);

        String start = clock.instant().toString();
        HttpResponse<String> inclusive = actor.get("/api/auth/admin/audit-log?from=" + start + "&action=STORE_ASSIGNED");
        assertThat(inclusive.statusCode()).isEqualTo(200);
        assertThat(inclusive.body()).contains(code, "Platform Administrator", "STORE_ASSIGNED", "PARTNER", "userId");
        HttpResponse<String> exclusive = actor.get("/api/auth/admin/audit-log?to=" + start + "&action=STORE_ASSIGNED");
        assertThat(exclusive.statusCode()).isEqualTo(200);
        assertThat(exclusive.body()).contains("\"total\":0");
        HttpResponse<String> filtered = actor.get("/api/auth/admin/audit-log?targetType=ASSIGNMENT&targetId=" + storeId);
        assertThat(filtered.body()).contains(code, "\"targetType\":\"ASSIGNMENT\"");
        assertThat(actor.get("/api/auth/admin/audit-log?from=not-an-instant").statusCode()).isEqualTo(400);
    }

    @Test
    void auditRowsAreAppendOnly() throws Exception {
        signedInBootstrapAdmin();
        boolean updateRejected = false;
        boolean deleteRejected = false;
        try {
            jdbcClient.sql("UPDATE core.audit_log SET detail = '{}'::jsonb WHERE audit_id = (SELECT min(audit_id) FROM core.audit_log)")
                    .update();
        } catch (org.springframework.dao.DataAccessException exception) {
            updateRejected = true;
        }
        try {
            jdbcClient.sql("DELETE FROM core.audit_log WHERE audit_id = (SELECT min(audit_id) FROM core.audit_log)")
                    .update();
        } catch (org.springframework.dao.DataAccessException exception) {
            deleteRejected = true;
        }
        assertTrue(updateRejected, "Audit updates must be rejected by PostgreSQL.");
        assertTrue(deleteRejected, "Audit deletes must be rejected by PostgreSQL.");
    }

    @Test
    void assignmentsEnforceRolesOwnerUniquenessAndAuditSemantics() throws Exception {
        AuthHttpClient actor = signedInBootstrapAdmin();
        String code = "AS_" + UUID.randomUUID();
        assertThat(createStore(actor, code, "Assignment Store", null, null, "US", "IN", "47872").statusCode())
                .isEqualTo(201);
        long storeId = storeId(code);
        String staffEmail = "assign.staff@test.local";
        String partnerEmail = "assign.partner@test.local";
        String ownerEmail = "assign.owner@test.local";
        String secondOwnerEmail = "assign.second@test.local";
        createUser(actor, "STAFF", staffEmail, "Assign Staff", UUID.randomUUID().toString() + "-test", null, null);
        createUser(actor, "CLIENT", partnerEmail, "Assign Partner", UUID.randomUUID().toString() + "-test", null, null);
        createUser(actor, "CLIENT", ownerEmail, "Assign Owner", UUID.randomUUID().toString() + "-test", null, null);
        createUser(actor, "CLIENT", secondOwnerEmail, "Second Owner", UUID.randomUUID().toString() + "-test", null, null);
        long staffId = storeUserId(staffEmail);
        long partnerId = storeUserId(partnerEmail);
        long ownerId = storeUserId(ownerEmail);
        long secondOwnerId = storeUserId(secondOwnerEmail);
        String path = "/api/auth/admin/stores/" + storeId + "/members/";

        assertThat(actor.send("PUT", path + staffId, "{\"storeRole\":\"STAFF\"}", true, null).statusCode()).isEqualTo(201);
        long assignedRows = jdbcClient.sql("SELECT count(*) FROM core.audit_log WHERE action = 'STORE_ASSIGNED' AND target_id = :id")
                .param("id", storeId).query(Long.class).single();
        assertThat(actor.send("PUT", path + staffId, "{\"storeRole\":\"STAFF\"}", true, null).statusCode()).isEqualTo(200);
        assertThat(jdbcClient.sql("SELECT count(*) FROM core.audit_log WHERE action = 'STORE_ASSIGNED' AND target_id = :id")
                .param("id", storeId).query(Long.class).single()).isEqualTo(assignedRows);
        assertThat(actor.send("PUT", path + partnerId, "{\"storeRole\":\"PARTNER\"}", true, null).statusCode()).isEqualTo(201);
        assertThat(actor.send("PUT", path + ownerId, "{\"storeRole\":\"OWNER\"}", true, null).statusCode()).isEqualTo(201);
        HttpResponse<String> secondOwner = actor.send("PUT", path + secondOwnerId,
                "{\"storeRole\":\"OWNER\"}", true, null);
        assertThat(secondOwner.statusCode()).isEqualTo(409);
        assertThat(secondOwner.body()).contains("OWNER_EXISTS");
        assertThat(actor.send("PUT", path + staffId, "{\"storeRole\":\"PARTNER\"}", true, null).statusCode())
                .isEqualTo(422);
        assertThat(actor.send("PUT", path + partnerId, "{\"storeRole\":\"STAFF\"}", true, null).statusCode())
                .isEqualTo(422);
        assertThat(actor.send("PUT", path + ownerId, "{\"storeRole\":\"PARTNER\"}", true, null).statusCode())
                .isEqualTo(200);
        assertThat(actor.send("PUT", path + partnerId, "{\"storeRole\":\"OWNER\"}", true, null).statusCode())
                .isEqualTo(200);

        HttpResponse<String> members = actor.get("/api/auth/admin/stores/" + storeId + "/members");
        assertThat(members.statusCode()).isEqualTo(200);
        assertThat(members.body()).contains("Assign Staff", "Assign Partner", "Platform Administrator", "OWNER", "STAFF");
        assertThat(jdbcClient.sql("SELECT count(*) FROM core.audit_log WHERE action = 'STORE_ASSIGNMENT_CHANGED' AND target_id = :id")
                .param("id", storeId).query(Long.class).single()).isEqualTo(2L);

        assertThat(actor.send("DELETE", path + staffId, null, true, null).statusCode()).isEqualTo(204);
        assertThat(actor.send("DELETE", path + staffId, null, true, null).statusCode()).isEqualTo(404);
    }

    @Test
    void inactiveStoresAndDisabledUsersCanBeAssignedAndRefreshRemovesInactiveAccess() throws Exception {
        AuthHttpClient actor = signedInBootstrapAdmin();
        String code = "IN_" + UUID.randomUUID();
        assertThat(createStore(actor, code, "Inactive Store", null, null, "US", "IN", "47872").statusCode())
                .isEqualTo(201);
        long storeId = storeId(code);
        String email = "inactive.client@test.local";
        String password = UUID.randomUUID().toString() + "-test";
        createUser(actor, "CLIENT", email, "Inactive Client", password, null, null);
        long userId = storeUserId(email);
        String storePath = "/api/auth/admin/stores/" + storeId;
        assertThat(actor.send("PUT", storePath + "/members/" + userId,
                "{\"storeRole\":\"PARTNER\"}", true, null).statusCode()).isEqualTo(201);

        AuthHttpClient user = new AuthHttpClient(port);
        assertThat(user.login(email, password).statusCode()).isEqualTo(200);
        assertThat(user.get("/api/auth/me").body()).contains(code);
        assertThat(actor.send("PATCH", storePath, "{\"status\":\"INACTIVE\"}", true, null).statusCode())
                .isEqualTo(200);
        assertThat(user.post("/api/auth/refresh").statusCode()).isEqualTo(204);
        assertThat(user.get("/api/auth/me").body()).contains("\"stores\":[]");

        jdbcClient.sql("UPDATE core.store_users SET status = 'DISABLED' WHERE user_id = :id")
                .param("id", userId).update();
        String otherCode = "OFF_" + UUID.randomUUID();
        assertThat(createStore(actor, otherCode, "Another Store", null, null, "US", "IN", "47872").statusCode())
                .isEqualTo(201);
        long otherStoreId = storeId(otherCode);
        assertThat(actor.send("PATCH", "/api/auth/admin/stores/" + otherStoreId,
                "{\"status\":\"INACTIVE\"}", true, null).statusCode()).isEqualTo(200);
        HttpResponse<String> assignedDisabled = actor.send("PUT",
                "/api/auth/admin/stores/" + otherStoreId + "/members/" + userId,
                "{\"storeRole\":\"PARTNER\"}", true, null);
        assertThat(assignedDisabled.statusCode()).isEqualTo(201);
        assertThat(assignedDisabled.body()).contains("DISABLED", "Platform Administrator");
    }

    @Test
    void storeCreateSearchPatchAndStatusRules() throws Exception {
        AuthHttpClient actor = signedInBootstrapAdmin();
        String code = "ST_" + UUID.randomUUID();
        HttpResponse<String> created = createStore(actor, code, "Example Store", "Suite 1", "555 555 0100",
                "us", "in", "90210");
        assertThat(created.statusCode()).isEqualTo(201);
        assertThat(created.body()).contains("Example Store", "\"stateCode\":\"IN\"", "\"countryCode\":\"US\"");
        long id = storeId(code);

        HttpResponse<String> searched = actor.get("/api/auth/admin/stores?q=" + code.toLowerCase(java.util.Locale.ROOT));
        assertThat(searched.statusCode()).isEqualTo(200);
        assertThat(searched.body()).contains(code, "Example Store", "\"memberCount\":0");

        HttpResponse<String> removedOptional = actor.send("PATCH", "/api/auth/admin/stores/" + id,
                "{\"addressLine2\":\"\",\"contactNumber\":\"\"}", true, null);
        assertThat(removedOptional.statusCode()).isEqualTo(200);
        assertThat(removedOptional.body()).doesNotContain("addressLine2", "contactNumber");
        assertThat(jdbcClient.sql("SELECT address_line2 IS NULL AND contact_number IS NULL FROM core.stores WHERE store_id = :id")
                .param("id", id).query(Boolean.class).single()).isTrue();

        HttpResponse<String> mixedStatus = actor.send("PATCH", "/api/auth/admin/stores/" + id,
                "{\"status\":\"INACTIVE\",\"city\":\"New City\"}", true, null);
        assertThat(mixedStatus.statusCode()).isEqualTo(422);
        assertThat(mixedStatus.body()).contains("STATUS_CHANGE_ALONE");
        assertThat(actor.send("PATCH", "/api/auth/admin/stores/" + id, "{\"storeCode\":\"OTHER\"}", true, null)
                .statusCode()).isEqualTo(422);
        assertThat(actor.send("PATCH", "/api/auth/admin/stores/" + id, "{\"posVendor\":\"GILBARCO\"}", true, null)
                .statusCode()).isEqualTo(422);
        HttpResponse<String> badTimezone = actor.send("PATCH", "/api/auth/admin/stores/" + id,
                "{\"timezone\":\"not/a-zone\"}", true, null);
        assertThat(badTimezone.statusCode()).isEqualTo(422);
        assertThat(badTimezone.body()).contains("INVALID_TIMEZONE");

        assertThat(actor.send("PATCH", "/api/auth/admin/stores/" + id, "{\"status\":\"INACTIVE\"}", true, null)
                .statusCode()).isEqualTo(200);
        assertThat(actor.send("PATCH", "/api/auth/admin/stores/" + id, "{\"status\":\"ACTIVE\"}", true, null)
                .statusCode()).isEqualTo(200);
    }

    @Test
    void storeValidationAndDuplicateCodeUseSpecifiedErrors() throws Exception {
        AuthHttpClient actor = signedInBootstrapAdmin();
        String code = "DUP_" + UUID.randomUUID();
        assertThat(createStore(actor, code, "Duplicate Store", null, null, "US", "IN", "47872").statusCode())
                .isEqualTo(201);
        HttpResponse<String> duplicate = createStore(actor, code, "Duplicate Store Again", null, null,
                "US", "IN", "47872", "different/" + code + "/");
        assertThat(duplicate.statusCode()).isEqualTo(409);
        assertThat(duplicate.body()).contains("STORE_CODE_TAKEN");

        String invalidCode = "ZIP_" + UUID.randomUUID();
        HttpResponse<String> invalidPostal = createStore(actor, invalidCode, "Bad Postal", null, null,
                "US", "IN", "bad-zip");
        assertThat(invalidPostal.statusCode()).isEqualTo(422);
        assertThat(invalidPostal.body()).contains("INVALID_POSTAL_CODE");
        HttpResponse<String> invalidFolder = createStore(actor, "FD_" + UUID.randomUUID(), "Bad Folder",
                null, null, "US", "IN", "47872", "/starts/with/slash/");
        assertThat(invalidFolder.statusCode()).as("problem response: " + invalidFolder.body()).isEqualTo(422);
        assertThat(invalidFolder.body()).contains("INVALID_S3_FOLDER");
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
        HttpResponse<String> disabledLogin = new AuthHttpClient(port).login(email, password);
        assertThat(disabledLogin.statusCode()).isEqualTo(403);
        assertThat(disabledLogin.body()).contains("ACCOUNT_DISABLED");
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

    private JWTClaimsSet accessClaims(AuthHttpClient client) throws java.text.ParseException {
        return SignedJWT.parse(client.cookieValue("hor_access")).getJWTClaimsSet();
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

    private HttpResponse<String> createStore(AuthHttpClient actor, String code, String name,
            String addressLine2, String contactNumber, String countryCode, String stateCode, String postalCode)
            throws Exception {
        return createStore(actor, code, name, addressLine2, contactNumber, countryCode, stateCode, postalCode,
                "Stores/" + code + "/");
    }

    private HttpResponse<String> createStore(AuthHttpClient actor, String code, String name,
            String addressLine2, String contactNumber, String countryCode, String stateCode, String postalCode,
            String s3Folder) throws Exception {
        String address2 = addressLine2 == null ? "" : ",\"addressLine2\":\"" + addressLine2 + "\"";
        String phone = contactNumber == null ? "" : ",\"contactNumber\":\"" + contactNumber + "\"";
        String body = "{\"storeCode\":\"" + code + "\",\"storeName\":\"" + name
                + "\",\"posVendor\":\"VERIFONE\",\"s3Folder\":\"" + s3Folder
                + "\",\"addressLine1\":\"100 Main Street\"" + address2
                + ",\"city\":\"Rockville\",\"stateCode\":\"" + stateCode
                + "\",\"postalCode\":\"" + postalCode + "\",\"countryCode\":\"" + countryCode
                + "\",\"timezone\":\"America/Indiana/Indianapolis\"" + phone + "}";
        return actor.send("POST", "/api/auth/admin/stores", body, true, null);
    }

    private long storeId(String code) {
        return jdbcClient.sql("SELECT store_id FROM core.stores WHERE store_code = :code")
                .param("code", code).query(Long.class).single();
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

    private boolean hasCauseMessage(Throwable exception, String fragment) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null && cause.getMessage().contains(fragment)) {
                return true;
            }
        }
        return false;
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

    @Test
    void syncStoresReturnsAllStoresForVendorOrderedByName() throws Exception {
        AuthHttpClient admin = signedInBootstrapAdmin();
        String vCode1 = "VSB_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String vCode2 = "VSA_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String gCode = "GS_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);

        HttpResponse<String> create1 = createStore(admin, vCode1, "Zulu Store", null, null, "US", "IN", "47872");
        assertThat(create1.statusCode()).isEqualTo(201);
        HttpResponse<String> create2 = createStore(admin, vCode2, "Alpha Store", null, null, "US", "IN", "47872");
        assertThat(create2.statusCode()).isEqualTo(201);

        long vId1 = storeId(vCode1);
        // Deactivate vCode1 to verify inactive stores are also returned
        HttpResponse<String> patchRes = admin.send("PATCH", "/api/auth/admin/stores/" + vId1, "{\"status\":\"INACTIVE\"}", true, null);
        assertThat(patchRes.statusCode()).isEqualTo(200);

        // Create a Gilbarco store to verify POS vendor filtering
        String gBody = "{\"storeCode\":\"" + gCode + "\",\"storeName\":\"Gilbarco Store\""
                + ",\"posVendor\":\"GILBARCO\",\"s3Folder\":\"Stores/" + gCode + "/\""
                + ",\"addressLine1\":\"100 Main Street\",\"city\":\"Rockville\",\"stateCode\":\"IN\""
                + ",\"postalCode\":\"47872\",\"countryCode\":\"US\",\"timezone\":\"America/Indiana/Indianapolis\"}";
        assertThat(admin.send("POST", "/api/auth/admin/stores", gBody, true, null).statusCode()).isEqualTo(201);

        // GET with lowercase vendor "verifone"
        HttpResponse<String> syncRes = admin.get("/api/auth/admin/stores/sync/verifone");
        assertThat(syncRes.statusCode()).isEqualTo(200);
        String body = syncRes.body();
        // Returns both active and inactive stores for Verifone
        assertThat(body).contains(vCode1);
        assertThat(body).contains(vCode2);
        // Does not include Gilbarco
        assertThat(body).doesNotContain(gCode);
        // Ordered by store_name ASC: Alpha Store before Zulu Store
        assertThat(body.indexOf("Alpha Store")).isLessThan(body.indexOf("Zulu Store"));
        // Confirm inactive status is present in response
        assertThat(body).contains("\"status\":\"INACTIVE\"");
        assertThat(body).contains("\"status\":\"ACTIVE\"");

        // Unknown vendor returns HTTP 400 with VALIDATION_FAILED
        HttpResponse<String> unknownRes = admin.get("/api/auth/admin/stores/sync/unknown");
        assertThat(unknownRes.statusCode()).isEqualTo(400);
        assertThat(unknownRes.body()).contains("VALIDATION_FAILED");
        assertThat(unknownRes.body()).contains("invalid pos vendor: unknown");

        // Unauthenticated call returns HTTP 401
        AuthHttpClient unauthenticated = new AuthHttpClient(port);
        HttpResponse<String> unauthRes = unauthenticated.get("/api/auth/admin/stores/sync/verifone");
        assertThat(unauthRes.statusCode()).isEqualTo(401);

        // Store user (non-admin) returns HTTP 403
        String userEmail = "sync.user." + UUID.randomUUID() + "@test.local";
        String userPassword = UUID.randomUUID().toString() + "-test";
        createUser(admin, "CLIENT", userEmail, "Sync User", userPassword, null, null);
        AuthHttpClient storeUser = new AuthHttpClient(port);
        assertThat(storeUser.login(userEmail, userPassword).statusCode()).isEqualTo(200);
        HttpResponse<String> forbiddenRes = storeUser.get("/api/auth/admin/stores/sync/verifone");
        assertThat(forbiddenRes.statusCode()).isEqualTo(403);
        assertThat(forbiddenRes.body()).contains("FORBIDDEN");
    }
}
