# hor-auth backend build report

## Delivered

- **auth-client:** Spring Boot auto-configuration, cookie-only JWT resolution, RS256/JWKS validation, `HorPrincipal`, store-access checks, role authorization helper, CSRF header enforcement, RFC 9457 problem responses, and request logging. Maven publishing produces `com.handsoff.hor:auth-client:0.1.0`.
- **auth-service:** PostgreSQL V1 schema and Flyway migration; first-start admin/store bootstrap; password hashing and lockout; access/refresh token issuance, rotation, revocation and cleanup; platform-admin and store-user management; store CRUD; store assignments; audit writing and query; JWKS endpoint; signing-key ACLs; loopback security configuration.
- **Infrastructure and operations:** PostgreSQL setup/grant scripts, nginx auth and apps-host location templates, systemd service template, and [RUNNING_LOCALLY.md](./RUNNING_LOCALLY.md), including manual signing-key rotation steps.
- **Not built:** `web/auth-console`, as excluded from this backend scope.

## Validation

| Build | Tests passed | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| auth-client | 17 | 0 | 0 | 0 |
| auth-service | 30 | 0 | 0 | 0 |

`gradlew.bat build` passed at the repository root with the included builds. Auth-service integration tests used the real PostgreSQL `auth_db_test`; there is no H2 or Docker test database. `auth-client` also completed `publishToMavenLocal` successfully.

## Owner-approved §16 decisions implemented

- Empty strings clear optional fields in PATCH; `null` leaves them unchanged.
- Status is changed alone; combining status with any other PATCH field returns `422 STATUS_CHANGE_ALONE`. An unchanged requested status still writes its corresponding audit action.
- A lock blocks password login only; an existing session can refresh.
- Own-admin password reset is permitted and revokes that admin's sessions. Unlocking an unlocked account still returns `204` and writes the unlock audit action.
- Audit `from` is inclusive and `to` exclusive. Pagination caps limits at 500, normalizes negative offsets to zero, treats limit zero as an empty page, and rejects negative limits with 400.
- auth-client uses `maven-publish` and publishes `com.handsoff.hor:auth-client:0.1.0`.

Other §16 overrides are implemented as specified: Gradle 9.8.1; PostgreSQL only; case-insensitive substring search; full-name audit/member labels; daily cleanup at 03:00 UTC; admin-row locking for concurrent logins; owner-only key permissions; nginx login rate limiting; security event logs without credentials; and no `aud` claim.

## Known limitations

- Store-access changes reach other services on refresh, with an accepted delay of up to 15 minutes.
- Cookies use `Domain=handsoffretail.com` in production. Cookie tossing from other subdomains remains a risk; the allowed-Origin CSRF check is the defense.
- Only one signing key is served at a time; rotation is manual and has no dual-key overlap.
- The auth-console is not built in this scope.
