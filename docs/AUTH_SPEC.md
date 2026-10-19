# hor-auth: build specification

This document is complete on its own. An engineer or an AI agent with this file (and `auth_db.mmd` next to it)
must be able to build, test and run the whole `hor-auth` system without asking anything else. Where this file
says **MUST**, do exactly that. Where something is not covered, **stop and ask the owner**. Never invent a
business rule.

---

## 0. What hor-auth is

HOR Stores loads point-of-sale data from convenience stores and shows reports to the store owners. It is three
independent systems behind one nginx, on two host names of the company domain `handsoffretail.com`:

| System (repo) | Contents | Talks to auth how |
|---|---|---|
| **hor-auth** (this spec) | auth-service (API), auth-console (React UI), auth-client (Java library) | n/a |
| hor-store-operations | verifone / gilbarco ingestion services, admin-console | uses auth-client; UI sends users to the auth-console login page |
| hor-retail-analytics | reporting-service, client-portal | uses auth-client; UI sends users to the auth-console login page |

hor-auth is the **only** place where people log in and where users, stores and store assignments are managed.
The other systems never call auth-service at run time except to download its public keys (JWKS) once and cache
them.

### In scope
1. **auth-service**: login, refresh, logout, "me", change my password; platform admins; store users (HOR staff
   and client users); stores; store assignments (owner / partner / staff); audit log; JWT signing + JWKS.
2. **auth-console**: the one login page for all HOR apps; "my account"; management screens for platform admins.
3. **auth-client**: Java library other Spring Boot services add to verify the auth cookie, enforce the CSRF rule,
   and check role and store access.
4. Database scripts and Flyway migration for `auth_db`.

### Out of scope (do not build)
Email sending, password-reset-by-email, MFA, social login, reports, ingestion, any UI other than auth-console.

---

## 1. Approved names (owner, 2026-10-18). Use exactly these.

| Kind | Name |
|---|---|
| Repo / folder | `C:\Users\abhia\OneDrive\HOR-Software\hor-stores\hor-auth\` |
| API service | `auth-service`, port **8091** |
| Web UI | `auth-console`, dev port **5180**, served at the root of `auth.handsoffretail.com` |
| Host names (production) | **`auth.handsoffretail.com`**: auth-console + `/api/auth`; **`apps.handsoffretail.com`**: admin-console `/admin/`, client-portal `/portal/` (other repos). DNS is set later; until then everything runs inside the VM |
| Java library | `auth-client` |
| Java packages | service `com.handsoff.hor.auth`, library `com.handsoff.hor.auth.client` |
| Maven coordinates | `com.handsoff.hor:auth-client:0.1.0` |
| Database | `auth_db`, schema `core`; tests `auth_db_test` |
| Database role | `auth_svc` (owner of auth_db) |
| API base path | `/api/auth/...` |
| Cookies | `hor_access` (access token), `hor_refresh` (refresh token) |
| Account roles | `PLATFORM_ADMIN`, `HOR_STAFF`, `CLIENT_USER` |
| Store roles | `OWNER`, `PARTNER`, `STAFF` |
| Property prefix | `hor.auth.*` |
| Secrets file | `%USERPROFILE%\.hor-stores\secrets.properties` |
| JWT signing key | `%USERPROFILE%\.hor-stores\auth-signing-key.pem` |
| CSRF header | `X-HOR-Request: 1` |
| Token issuer (`iss`) | `hor-auth` |

---

## 2. Owner decisions (business rules). Do not change without the owner.

### Accounts and roles
- **Two user tables.** `platform_admins` holds platform admins. `store_users` holds HOR staff
  (`user_type = STAFF`, role `HOR_STAFF`) and client users (`user_type = CLIENT`, role `CLIENT_USER`).
- **One email = one account.** An email belongs to an admin *or* a store user, never both (table
  `account_emails`). Emails ignore upper / lower case.
- **Only PLATFORM_ADMIN creates or changes data** (users, stores, assignments). HOR staff and clients can only
  log in, use the client portal for their assigned stores, change their own password and log out. What else
  HOR staff may do is decided later; build nothing for it.
- **First password:** the platform admin types it when creating the account and tells the user. Minimum 12
  characters. The user may change it any time. **A password change is never forced.**
- **Lockout:** 10 wrong passwords in a row lock the account for 15 minutes. Same for all account types. A
  platform admin can unlock early. A successful login resets the counter.
- **Disable, never delete.** Disabling an account blocks login and ends its sessions. Store assignments stay;
  enabling restores access. Accounts are never deleted.
- `user_type` of a store user (STAFF / CLIENT) cannot change after creation.

### Sessions
- **PLATFORM_ADMIN: one active session.** A new login anywhere ends every earlier session of that admin. The
  old browser stops at its next refresh (at most 15 minutes later). This delay is accepted.
- **HOR_STAFF / CLIENT_USER: many sessions** (several devices at once).
- **Log out** ends this browser's session for all HOR apps.
- **Single sign-on:** auth on `auth.handsoffretail.com`, the other apps on `apps.handsoffretail.com`
  (`/admin/`, `/portal/`). Both cookies carry `Domain=handsoffretail.com` in production, so they reach both
  hosts. Log in once, use every app the role allows.

### Which app each role may use

| Role | auth-console | admin-console (`/admin/`) | client-portal (`/portal/`) |
|---|---|---|---|
| PLATFORM_ADMIN | login, my account, all management screens | yes | yes, **all** stores |
| HOR_STAFF | login, my account | no | assigned **active** stores only |
| CLIENT_USER | login, my account | no | assigned **active** stores only |

### Stores and assignments
- **No companies.** A store has **at most one OWNER** client, possibly none (a store may be created before its
  client). Other clients of the store are **PARTNERs**. A partner may be assigned to a store that has no owner.
- HOR staff are assigned with store role `STAFF` and may be assigned stores of any number of clients.
- Clients may only be `OWNER` or `PARTNER`; staff may only be `STAFF`.
- Owners, partners and staff see the **same** reports for their stores.
- **Deactivated stores are hidden** from staff and clients (they leave the token's store list at the next
  refresh, within 15 minutes). Assignments stay; re-activating shows the store again. Platform admins still see
  it.
- Store-access changes may take up to 15 minutes to reach other services (the store list travels inside the
  access token). Accepted.
- `store_code` and `pos_vendor` cannot change after creation.

### First start (empty database)
In one transaction, each step only when its table is empty:
1. Create the first platform admin from the secrets file (`hor.auth.bootstrap.admin.email`, `.password`) and
   `hor.auth.bootstrap.admin.full-name` (properties). If `platform_admins` is empty and the secrets are missing,
   **refuse to start** with a clear message. After creating it, log once: "First platform admin created; remove
   hor.auth.bootstrap.admin.password from the secrets file." (never log the password).
2. Create the store **Tiki Hut** from `hor.auth.bootstrap.default-store.*` (values in §9) with **no owner**.
3. Audit rows `ADMIN_CREATED` and `STORE_CREATED`, actor = the first admin.

---

## 3. Architecture of hor-auth

```
Browser ──HTTPS──> nginx :443 ── auth.handsoffretail.com  /           auth-console static files
                              │                          /api/auth/   auth-service 127.0.0.1:8091 ──> PostgreSQL auth_db
                              └─ apps.handsoffretail.com  /admin/, /portal/, their APIs (other repos)
                                                         /api/auth/   same auth-service (so refresh/logout are same-origin there)
Other services on the VM ──GET http://127.0.0.1:8091/api/auth/.well-known/jwks.json (cached)──> auth-service
Other services read auth_db.core.stores read-only (SELECT grant), never write.
```

- auth-service is a standalone Spring Boot app. It depends only on PostgreSQL and its key file.
- **Inside the VM only:** every service listens on `127.0.0.1` (`server.address`); services talk to each other
  on `127.0.0.1`. nginx is the only process reachable from outside (ports 80/443), once DNS and TLS exist.
- If auth-service is down: nobody can log in or refresh; already-issued access tokens keep working until they
  expire (≤ 15 min) because other services verify tokens locally.
- No message broker, no service-to-service calls besides JWKS.

---

## 4. Tech stack (exact versions)

| Part | Choice |
|---|---|
| Language / runtime | Java 21 (Temurin) |
| Framework | Spring Boot **4.1.1** (Spring Framework 7, Spring Security 7, Jackson 3 `tools.jackson`) |
| Build | Gradle **9.7.1** wrapper, Kotlin DSL; versions in `gradle.properties`; plugin `io.spring.dependency-management` **1.1.7** |
| Config | `application.properties` (tests: `application-test.properties`), **no YAML** |
| Data access | plain JDBC (`JdbcClient`), Flyway (`spring-boot-starter-flyway` + `flyway-database-postgresql`), no JPA |
| Database | PostgreSQL 17 locally, **PostgreSQL 16 on the server**: use no SQL newer than 16 (no `MERGE ... RETURNING`, no JSON_TABLE) |
| JWT | Nimbus JOSE + JWT (from the Boot BOM), RS256, 2048-bit RSA |
| Passwords | BCrypt (Spring Security `BCryptPasswordEncoder`), strength 12 (tests: 4) |
| Verification in other services | Spring Security OAuth2 resource server (`NimbusJwtDecoder` with JWKS) via auth-client |
| Web UI | React **19.2**, **JSX + JavaScript (no TypeScript)**, Vite **8**, react-router **7**, oxlint, plain CSS |
| Tests | JUnit 5, Spring Boot test, AssertJ; real PostgreSQL `auth_db_test` (no Docker, no H2) |
| Server (later) | Oracle Cloud Ubuntu VM, nginx + Let's Encrypt, systemd |

Spring Boot 4 package names that differ from Boot 3 (use these):
`org.springframework.boot.web.servlet.FilterRegistrationBean`,
`org.springframework.boot.jdbc.autoconfigure.DataSourceProperties`,
`org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy`,
`org.springframework.boot.test.web.server.LocalServerPort`; starters `spring-boot-starter-webmvc`,
`spring-boot-starter-flyway`, `spring-boot-starter-security`, `spring-boot-starter-oauth2-resource-server`,
`spring-boot-starter-validation`, `spring-boot-starter-jdbc`.

---

## 5. Repository layout

```
hor-auth/
├── settings.gradle.kts         root aggregate: includeBuild("libs/auth-client"), includeBuild("services/auth-service")
├── build.gradle.kts            plugins { base }; build/check/assemble/clean depend on the included builds
├── gradle.properties           org.gradle.workers.max=1, org.gradle.caching=true
├── gradlew, gradlew.bat, gradle/wrapper/
├── libs/auth-client/           standalone java-library build (own gradlew, settings.gradle.kts, gradle.properties)
├── services/auth-service/      standalone Spring Boot build (own gradlew, settings.gradle.kts, gradle.properties)
│                               settings.gradle.kts: includeBuild("../../libs/auth-client")
├── web/auth-console/           React 19 + JSX + Vite
├── infra/postgres/             01_auth_database.sql, 02_auth_test_database.sql, 03_grant_store_reader.sql
├── infra/nginx/auth.conf       server auth.handsoffretail.com + the /api/auth/ block for apps.handsoffretail.com (template)
├── infra/systemd/auth-service.service   (template)
├── docs/AUTH_SPEC.md (this file), auth_db.mmd, RUNNING_LOCALLY.md, BUILD_REPORT.md
├── .gitignore                  build/, .gradle/, node_modules/, dist/, *.pem, secrets*, *.log, local.properties
└── .gitattributes              * text=auto; *.bat eol=crlf; gradlew eol=lf
```

Each standalone build's `settings.gradle.kts` reads plugin versions from its `gradle.properties`:

```kotlin
pluginManagement {
    plugins {
        id("org.springframework.boot") version providers.gradleProperty("springBootVersion").get()
        id("io.spring.dependency-management") version providers.gradleProperty("dependencyManagementVersion").get()
    }
}
rootProject.name = "auth-service"
includeBuild("../../libs/auth-client")
```

`gradle.properties` of each build: `springBootVersion=4.1.1`, `dependencyManagementVersion=1.1.7`,
`org.gradle.jvmargs=-Xmx1g -Dfile.encoding=UTF-8`, `org.gradle.caching=true`.
auth-service depends on `implementation("com.handsoff.hor:auth-client:0.1.0")` (substituted by the included
build). Java toolchain 21; compiler args `-parameters -Xlint:all,-processing,-serial`; tests `useJUnitPlatform()`,
`maxParallelForks = 1`. `bootJar` named `auth-service.jar`; plain `jar` disabled.

---

## 6. Database

### 6.1 Setup scripts (`infra/postgres/`, run once as the PostgreSQL superuser)

`01_auth_database.sql` (psql variable `auth_pw` from the secrets file):
```sql
CREATE ROLE auth_svc LOGIN PASSWORD :'auth_pw';
CREATE DATABASE auth_db OWNER auth_svc ENCODING 'UTF8' TEMPLATE template0;
REVOKE ALL ON DATABASE auth_db FROM PUBLIC;
GRANT CONNECT, TEMPORARY ON DATABASE auth_db TO auth_svc;
```
`02_auth_test_database.sql`: same for `auth_db_test` (owner `auth_svc`), local machines only.

`03_grant_store_reader.sql` (psql variable `reader_role`; run after the first auth-service start, once per
consuming service, e.g. `verifone_ingest_svc`):
```sql
GRANT CONNECT ON DATABASE auth_db TO :"reader_role";
\c auth_db
GRANT USAGE ON SCHEMA core TO :"reader_role";
GRANT SELECT ON core.stores TO :"reader_role";
```

### 6.2 Flyway migration `services/auth-service/src/main/resources/db/migration/V1__auth_schema.sql`

Rules: every table has a PK, natural keys UNIQUE, every FK indexed (or covered by a leading index column),
CHECKs for fixed value sets, all moments `timestamptz`. `citext` is a trusted extension (PG ≥ 13), so the
database owner may create it. **An applied migration is never edited; changes go in `V2__...`, `V3__...`.**

```sql
CREATE EXTENSION IF NOT EXISTS citext;
CREATE SCHEMA core;

-- One email = one account (admin OR store user)
CREATE TABLE core.account_emails (
    email        citext      PRIMARY KEY,
    account_kind text        NOT NULL,
    created_at   timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_account_emails_kind  CHECK (account_kind IN ('ADMIN', 'STORE_USER')),
    CONSTRAINT ck_account_emails_email CHECK (length(email) <= 254 AND email ~ '^[^@\s]+@[^@\s]+\.[^@\s]+$'),
    CONSTRAINT uq_account_emails_email_kind UNIQUE (email, account_kind)
);

CREATE TABLE core.platform_admins (
    admin_id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    full_name           text        NOT NULL,
    email               citext      NOT NULL,
    account_kind        text        NOT NULL DEFAULT 'ADMIN',
    phone_number        text,
    password_hash       text        NOT NULL,
    status              text        NOT NULL DEFAULT 'ACTIVE',
    failed_login_count  integer     NOT NULL DEFAULT 0,
    locked_until        timestamptz,
    password_changed_at timestamptz NOT NULL,
    last_login_at       timestamptz,
    created_at          timestamptz NOT NULL,
    updated_at          timestamptz NOT NULL,
    CONSTRAINT uq_platform_admins_email UNIQUE (email),
    CONSTRAINT fk_platform_admins_email FOREIGN KEY (email, account_kind)
        REFERENCES core.account_emails (email, account_kind) ON UPDATE CASCADE,
    CONSTRAINT ck_platform_admins_kind      CHECK (account_kind = 'ADMIN'),
    CONSTRAINT ck_platform_admins_status    CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT ck_platform_admins_name      CHECK (length(btrim(full_name)) BETWEEN 1 AND 200),
    CONSTRAINT ck_platform_admins_phone     CHECK (phone_number ~ '^\+?[0-9 ()-]{7,20}$'),
    CONSTRAINT ck_platform_admins_failed    CHECK (failed_login_count >= 0)
);

CREATE TABLE core.store_users (
    user_id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_type           text        NOT NULL,
    full_name           text        NOT NULL,
    email               citext      NOT NULL,
    account_kind        text        NOT NULL DEFAULT 'STORE_USER',
    phone_number        text,
    address             text,
    password_hash       text        NOT NULL,
    status              text        NOT NULL DEFAULT 'ACTIVE',
    failed_login_count  integer     NOT NULL DEFAULT 0,
    locked_until        timestamptz,
    password_changed_at timestamptz NOT NULL,
    last_login_at       timestamptz,
    created_by          bigint      NOT NULL REFERENCES core.platform_admins (admin_id),
    created_at          timestamptz NOT NULL,
    updated_at          timestamptz NOT NULL,
    CONSTRAINT uq_store_users_email UNIQUE (email),
    CONSTRAINT uq_store_users_id_type UNIQUE (user_id, user_type),
    CONSTRAINT fk_store_users_email FOREIGN KEY (email, account_kind)
        REFERENCES core.account_emails (email, account_kind) ON UPDATE CASCADE,
    CONSTRAINT ck_store_users_type    CHECK (user_type IN ('CLIENT', 'STAFF')),
    CONSTRAINT ck_store_users_kind    CHECK (account_kind = 'STORE_USER'),
    CONSTRAINT ck_store_users_status  CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT ck_store_users_name    CHECK (length(btrim(full_name)) BETWEEN 1 AND 200),
    CONSTRAINT ck_store_users_phone   CHECK (phone_number ~ '^\+?[0-9 ()-]{7,20}$'),
    CONSTRAINT ck_store_users_address CHECK (length(address) <= 500),
    CONSTRAINT ck_store_users_failed  CHECK (failed_login_count >= 0)
);
CREATE INDEX ix_store_users_created_by ON core.store_users (created_by);

CREATE TABLE core.stores (
    store_id       bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    store_code     text        NOT NULL,
    store_name     text        NOT NULL,
    pos_vendor     text        NOT NULL,
    s3_folder      text        NOT NULL,
    address_line1  text        NOT NULL,
    address_line2  text,
    city           text        NOT NULL,
    state_code     text        NOT NULL,
    postal_code    text        NOT NULL,
    country_code   text        NOT NULL DEFAULT 'US',
    contact_number text,
    timezone       text        NOT NULL,
    status         text        NOT NULL DEFAULT 'ACTIVE',
    created_at     timestamptz NOT NULL,
    updated_at     timestamptz NOT NULL,
    CONSTRAINT uq_stores_store_code UNIQUE (store_code),
    CONSTRAINT uq_stores_s3_folder  UNIQUE (s3_folder),
    CONSTRAINT ck_stores_code     CHECK (store_code ~ '^[A-Za-z0-9][A-Za-z0-9_-]{0,39}$'),
    CONSTRAINT ck_stores_name     CHECK (length(btrim(store_name)) BETWEEN 1 AND 200),
    CONSTRAINT ck_stores_vendor   CHECK (pos_vendor IN ('VERIFONE', 'GILBARCO')),
    CONSTRAINT ck_stores_s3       CHECK (s3_folder ~ '^[^/].*/$' AND strpos(s3_folder, store_code) > 0),
    CONSTRAINT ck_stores_state    CHECK (state_code ~ '^[A-Z]{2}$'),
    CONSTRAINT ck_stores_country  CHECK (country_code ~ '^[A-Z]{2}$'),
    CONSTRAINT ck_stores_zip      CHECK (country_code <> 'US' OR postal_code ~ '^[0-9]{5}(-[0-9]{4})?$'),
    CONSTRAINT ck_stores_phone    CHECK (contact_number ~ '^\+?[0-9 ()-]{7,20}$'),
    CONSTRAINT ck_stores_timezone CHECK (timezone ~ '^[A-Za-z_]+(/[A-Za-z0-9_+-]+)+$'),
    CONSTRAINT ck_stores_status   CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE TABLE core.store_user_stores (
    user_id     bigint      NOT NULL,
    store_id    bigint      NOT NULL REFERENCES core.stores (store_id),
    user_type   text        NOT NULL,
    store_role  text        NOT NULL,
    assigned_by bigint      NOT NULL REFERENCES core.platform_admins (admin_id),
    created_at  timestamptz NOT NULL,
    updated_at  timestamptz NOT NULL,
    PRIMARY KEY (user_id, store_id),
    CONSTRAINT fk_store_user_stores_user FOREIGN KEY (user_id, user_type)
        REFERENCES core.store_users (user_id, user_type),
    CONSTRAINT ck_store_user_stores_role CHECK (
        (user_type = 'CLIENT' AND store_role IN ('OWNER', 'PARTNER'))
        OR (user_type = 'STAFF' AND store_role = 'STAFF'))
);
CREATE UNIQUE INDEX uq_store_user_stores_one_owner ON core.store_user_stores (store_id) WHERE store_role = 'OWNER';
CREATE INDEX ix_store_user_stores_store       ON core.store_user_stores (store_id);
CREATE INDEX ix_store_user_stores_assigned_by ON core.store_user_stores (assigned_by);

CREATE TABLE core.admin_refresh_tokens (
    token_id    bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    admin_id    bigint      NOT NULL REFERENCES core.platform_admins (admin_id),
    token_hash  bytea       NOT NULL,
    replaced_by bigint      REFERENCES core.admin_refresh_tokens (token_id),
    expires_at  timestamptz NOT NULL,
    revoked_at  timestamptz,
    created_at  timestamptz NOT NULL,
    CONSTRAINT uq_admin_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT ck_admin_refresh_tokens_hash CHECK (octet_length(token_hash) = 32),
    CONSTRAINT ck_admin_refresh_tokens_replaced CHECK (replaced_by IS NULL OR revoked_at IS NOT NULL)
);
CREATE INDEX ix_admin_refresh_tokens_admin    ON core.admin_refresh_tokens (admin_id);
CREATE INDEX ix_admin_refresh_tokens_replaced ON core.admin_refresh_tokens (replaced_by);

CREATE TABLE core.user_refresh_tokens (
    token_id    bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     bigint      NOT NULL REFERENCES core.store_users (user_id),
    token_hash  bytea       NOT NULL,
    replaced_by bigint      REFERENCES core.user_refresh_tokens (token_id),
    expires_at  timestamptz NOT NULL,
    revoked_at  timestamptz,
    created_at  timestamptz NOT NULL,
    CONSTRAINT uq_user_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT ck_user_refresh_tokens_hash CHECK (octet_length(token_hash) = 32),
    CONSTRAINT ck_user_refresh_tokens_replaced CHECK (replaced_by IS NULL OR revoked_at IS NOT NULL)
);
CREATE INDEX ix_user_refresh_tokens_user     ON core.user_refresh_tokens (user_id);
CREATE INDEX ix_user_refresh_tokens_replaced ON core.user_refresh_tokens (replaced_by);

CREATE TABLE core.audit_log (
    audit_id       bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    actor_admin_id bigint      NOT NULL REFERENCES core.platform_admins (admin_id),
    action         text        NOT NULL,
    target_type    text        NOT NULL,
    target_id      bigint      NOT NULL,
    detail         jsonb       NOT NULL DEFAULT '{}',
    created_at     timestamptz NOT NULL,
    CONSTRAINT ck_audit_log_target CHECK (target_type IN ('ADMIN', 'USER', 'STORE', 'ASSIGNMENT')),
    CONSTRAINT ck_audit_log_action CHECK (action IN (
        'ADMIN_CREATED', 'ADMIN_UPDATED', 'ADMIN_DISABLED', 'ADMIN_ENABLED', 'ADMIN_PASSWORD_RESET', 'ADMIN_UNLOCKED',
        'USER_CREATED', 'USER_UPDATED', 'USER_DISABLED', 'USER_ENABLED', 'USER_PASSWORD_RESET', 'USER_UNLOCKED',
        'STORE_CREATED', 'STORE_UPDATED', 'STORE_DEACTIVATED', 'STORE_ACTIVATED',
        'STORE_ASSIGNED', 'STORE_ASSIGNMENT_CHANGED', 'STORE_UNASSIGNED'))
);
CREATE INDEX ix_audit_log_actor   ON core.audit_log (actor_admin_id);
CREATE INDEX ix_audit_log_created ON core.audit_log (created_at DESC);

-- The audit log is append-only.
CREATE FUNCTION core.audit_log_append_only() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'core.audit_log is append-only';
END $$;
CREATE TRIGGER tr_audit_log_append_only BEFORE UPDATE OR DELETE ON core.audit_log
    FOR EACH ROW EXECUTE FUNCTION core.audit_log_append_only();
```

Notes for the code:
- **Changing an email:** `UPDATE core.account_emails SET email = :new WHERE email = :old` (cascades to the user
  row). A clash raises the PK violation → `409 EMAIL_TAKEN`.
- **Creating an account:** insert into `account_emails` first, then the user row, in one transaction.
- **Assignment target in the audit log:** `target_type = 'ASSIGNMENT'`, `target_id = store_id`,
  `detail = {"userId": …, "storeRole": …, "previousRole": …}`.
- `updated_at` / `created_at` are set by the application from the injected `Clock`.
- Map constraint names to API errors in one place (`uq_account_emails…`/`account_emails_pkey` → `EMAIL_TAKEN`,
  `uq_stores_store_code` → `STORE_CODE_TAKEN`, `uq_stores_s3_folder` → `S3_FOLDER_TAKEN`,
  `uq_store_user_stores_one_owner` → `OWNER_EXISTS`, `ck_*` → `422 INVALID_<FIELD>`).
- A daily job deletes refresh-token rows whose `expires_at` or `revoked_at` is older than 30 days.

---

## 7. Tokens, cookies and sessions

### 7.1 Access token (JWT, RS256)
- Header: `alg=RS256`, `kid` = RFC 7638 thumbprint of the public key.
- Claims:

| Claim | Value |
|---|---|
| `iss` | `hor-auth` |
| `sub` | account id as a string (`admin_id` or `user_id`) |
| `typ` | `ADMIN` or `STORE_USER` (tells which table `sub` is from) |
| `role` | `PLATFORM_ADMIN`, `HOR_STAFF` or `CLIENT_USER` |
| `name` | full name (display only) |
| `all_stores` | `true` for PLATFORM_ADMIN only |
| `stores` | STORE_USER only: array of `store_id` of **ACTIVE** stores assigned to the user, ascending |
| `iat`, `exp` | issued-at; `exp = iat + 15 min` |
| `jti` | random UUID |

- No email, phone or other personal data in the token. Cookie size limit 4 KB: if a store user has more than
  300 stores, fail the token issue with a clear error (ask the owner if that ever happens).

### 7.2 Refresh token
- Value: `a.` (admin) or `u.` (store user) + 32 random bytes from `SecureRandom`, base64url without padding.
  The prefix says which table to look in.
- Stored only as SHA-256 of the whole cookie value (`token_hash`, 32 bytes). Never stored or logged raw.
- Lifetime: 12 hours, **sliding** (every refresh issues a new token valid 12 hours).

### 7.3 Cookies (both httpOnly; JavaScript never sees a token)

| Cookie | Value | Path | Max-Age | Flags |
|---|---|---|---|---|
| `hor_access` | access JWT | `/` | 900 | `HttpOnly; Secure; SameSite=Lax` |
| `hor_refresh` | refresh token | `/api/auth` | 43200 | `HttpOnly; Secure; SameSite=Lax` |

- `Domain` = `hor.auth.cookie-domain`: **`handsoffretail.com`** in production (cookies reach `auth.` and `apps.`);
  **empty in development** (host-only `localhost` cookie). Never another value.
- `Secure` is always on. Browsers accept Secure cookies on `http://localhost`, so local development works.
  Cookies ignore ports, so the dev UIs on 5180 / 5181 / 5182 share them too.
- Clearing a cookie: same name and Path, empty value, `Max-Age=0`.

### 7.4 CSRF and request rules (owner: SameSite=Lax + header)
Enforced by a filter in auth-client, so every HOR service applies the same rule:
1. For `POST`, `PUT`, `PATCH`, `DELETE`: the request **must** carry `X-HOR-Request: 1`, else `403 CSRF_REJECTED`.
2. For those methods, if an `Origin` header is present it **must** be in `hor.auth.allowed-origins`; if absent,
   `Referer` (when present) must start with an allowed origin; else `403 CSRF_REJECTED`.
3. No CORS: every app calls `/api/auth/...` on its **own** host (nginx proxies `/api/auth/` on both
   `auth.` and `apps.`; the Vite proxy does it in development), so requests are always same-origin. Do not
   enable CORS.

### 7.5 Session rules
- **Login (admin):** in one transaction: revoke every non-revoked `admin_refresh_tokens` row of the admin
  (`revoked_at = now`), insert the new token, set `last_login_at`, reset `failed_login_count`, clear
  `locked_until`.
- **Login (store user):** same, but **do not** revoke other sessions.
- **Refresh** (`POST /api/auth/refresh`, cookie `hor_refresh`):
  1. Missing / unknown / expired → `401 SESSION_ENDED`, clear both cookies.
  2. Already revoked **and** `replaced_by` set (a rotated token used again = theft or a bug) → revoke the whole
     chain forward from it (recursive CTE over `replaced_by`), `401 SESSION_ENDED`, clear cookies.
  3. Already revoked without `replaced_by` (logged out / kicked out) → `401 SESSION_ENDED`, clear cookies.
  4. Account `DISABLED` → revoke, `401 SESSION_ENDED`.
  5. Otherwise rotate atomically: `UPDATE … SET revoked_at = now, replaced_by = :new WHERE token_id = :old AND
     revoked_at IS NULL`; if 0 rows were updated (a concurrent refresh won), treat as rule 2. Insert the new token,
     issue a new access token with the current role and store list, set both cookies, `204`.
- **Logout:** revoke the current refresh token (and its forward chain), clear both cookies, `204` even without a
  cookie.
- **Admin password reset / disable of an account:** revoke all its refresh tokens.
- **Change own password:** revoke all other refresh tokens of the account, rotate the current one.
- Tabs and apps share the cookies. The UI MUST serialize refreshes (see §11.3) so two tabs never rotate the same
  token.

### 7.6 Signing key
- File `hor.auth.signing-key-path` (default `${user.home}/.hor-stores/auth-signing-key.pem`): RSA 2048
  private key, PKCS#8 PEM. If missing at start-up, generate it and write it (owner-only file permissions where
  the OS supports it). Never log it.
- JWKS: `GET /api/auth/.well-known/jwks.json` → `{"keys":[{kty, n, e, kid, use:"sig", alg:"RS256"}]}`,
  `Cache-Control: public, max-age=3600`.
- auth-service verifies its own cookies with the in-memory public key (no HTTP call to itself).

---

## 8. auth-service API (`/api/auth`)

Conventions:
- JSON, UTF-8. Dates ISO-8601 (`2026-10-18T10:15:30Z`). Lists: `?limit=` (default 50, max 500) `&offset=`,
  response `{"items":[…],"total":n,"limit":l,"offset":o}`.
- Errors: RFC 9457 `application/problem+json` with `status`, `title`, `detail`, `code`, and for 400 a list
  `errors: [{field, message}]`. Codes below.
- Every write needs `X-HOR-Request: 1` (§7.4). Every admin endpoint needs role `PLATFORM_ADMIN` → else
  `403 FORBIDDEN`. Not logged in → `401 UNAUTHENTICATED`; expired access cookie → `401 TOKEN_EXPIRED`.
- Unknown ids → `404 NOT_FOUND`.

### 8.1 Session

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| POST | `/login` | `{email, password}` | `200 Me` + both cookies | `401 BAD_CREDENTIALS` (unknown email, wrong password); `403 ACCOUNT_DISABLED` (only after a correct password); `423 ACCOUNT_LOCKED` with `lockedUntil` |
| POST | `/refresh` | none | `204` + both cookies | `401 SESSION_ENDED` |
| POST | `/logout` | none | `204`, cookies cleared | |
| GET | `/me` | | `200 Me` | |
| POST | `/me/password` | `{currentPassword, newPassword}` | `204` | `422 WRONG_PASSWORD`, `400` if new < 12 chars |
| GET | `/.well-known/jwks.json` | | `200` | public |

`Me`:
```json
{ "accountType": "ADMIN|STORE_USER", "id": 7, "email": "a@b.com", "fullName": "…",
  "role": "PLATFORM_ADMIN|HOR_STAFF|CLIENT_USER", "allStores": true,
  "stores": [{"storeId": 1, "storeCode": "8730_IN-47872", "storeName": "Tiki Hut", "storeRole": "OWNER"}],
  "passwordChangedAt": "…", "lastLoginAt": "…" }
```
(`stores` lists active assigned stores for store users; empty for admins, who have `allStores: true`.)

Login details:
- Look up the email in `account_emails` to know the table. Unknown email: still run one BCrypt check against a
  fixed dummy hash (equal timing), then `401 BAD_CREDENTIALS`.
- If `locked_until > now` → `423` without checking the password.
- Wrong password: `UPDATE … SET failed_login_count = failed_login_count + 1, locked_until = CASE WHEN
  failed_login_count + 1 >= 10 THEN now + 15 min END` in one statement, committed even though the request
  fails; the 10th wrong password answers `423`.
- Correct password on a DISABLED account → `403 ACCOUNT_DISABLED`.
- Passwords: 12–72 characters (BCrypt limit 72 bytes; check UTF-8 byte length ≤ 72).

### 8.2 Platform admins (`PLATFORM_ADMIN`)

| Method | Path | Body / query | Notes |
|---|---|---|---|
| GET | `/admin/platform-admins` | `?status&q&limit&offset` | `q` matches name or email |
| POST | `/admin/platform-admins` | `{email, fullName, phoneNumber?, password}` | `201`; audit `ADMIN_CREATED` |
| GET | `/admin/platform-admins/{id}` | | |
| PATCH | `/admin/platform-admins/{id}` | any of `{email, fullName, phoneNumber, status}` | `phoneNumber: ""` removes it; status change → `ADMIN_DISABLED` / `ADMIN_ENABLED`, other fields → `ADMIN_UPDATED` |
| POST | `/admin/platform-admins/{id}/password-reset` | `{newPassword}` | `204`; ends their sessions; `ADMIN_PASSWORD_RESET` |
| POST | `/admin/platform-admins/{id}/unlock` | | clears lock and counter; `ADMIN_UNLOCKED` |

Rules: an admin cannot disable themself (`422 CANNOT_DISABLE_SELF`); the last ACTIVE admin cannot be disabled
(`422 LAST_ACTIVE_ADMIN`).

`PlatformAdmin` response: `{id, email, fullName, phoneNumber, status, locked, lockedUntil, failedLoginCount,
passwordChangedAt, lastLoginAt, createdAt}` (never the hash).

### 8.3 Store users: HOR staff and client users (`PLATFORM_ADMIN`)

| Method | Path | Body / query | Notes |
|---|---|---|---|
| GET | `/admin/users` | `?userType=STAFF|CLIENT&status&q&limit&offset` | |
| POST | `/admin/users` | `{userType, email, fullName, phoneNumber?, address?, password}` | `201`; `USER_CREATED` |
| GET | `/admin/users/{id}` | | includes `stores` (all assignments, active and inactive stores, with `storeRole`, `storeStatus`) |
| PATCH | `/admin/users/{id}` | any of `{email, fullName, phoneNumber, address, status}` | `userType` not accepted (`422 USER_TYPE_FIXED`); status → `USER_DISABLED` / `USER_ENABLED` (disable ends sessions) |
| POST | `/admin/users/{id}/password-reset` | `{newPassword}` | ends their sessions; `USER_PASSWORD_RESET` |
| POST | `/admin/users/{id}/unlock` | | `USER_UNLOCKED` |

`StoreUser` response: `{id, userType, role, email, fullName, phoneNumber, address, status, locked, lockedUntil,
failedLoginCount, passwordChangedAt, lastLoginAt, createdAt, storeCount}`.

### 8.4 Stores (`PLATFORM_ADMIN`)

| Method | Path | Body / query | Notes |
|---|---|---|---|
| GET | `/admin/stores` | `?status&posVendor&q&limit&offset` | each row includes `ownerName` (or null) and `memberCount` |
| POST | `/admin/stores` | `{storeCode, storeName, posVendor, s3Folder, addressLine1, addressLine2?, city, stateCode, postalCode, countryCode?, contactNumber?, timezone}` | `201`; `STORE_CREATED` |
| GET | `/admin/stores/{id}` | | |
| PATCH | `/admin/stores/{id}` | any field except `storeCode`, `posVendor`; plus `status` | `422 STORE_CODE_FIXED` / `POS_VENDOR_FIXED`; status → `STORE_DEACTIVATED` / `STORE_ACTIVATED`, else `STORE_UPDATED` |

Validation (before the database): `timezone` must parse with `java.time.ZoneId.of` (`422 INVALID_TIMEZONE`);
`stateCode`, `countryCode` upper-cased; US postal code 5 or 5+4 digits; `s3Folder` ends with `/`, does not start
with `/`, contains `storeCode`.

### 8.5 Store assignments (`PLATFORM_ADMIN`)

| Method | Path | Body | Notes |
|---|---|---|---|
| GET | `/admin/stores/{storeId}/members` | | `[{userId, userType, fullName, email, status, storeRole, assignedAt, assignedBy}]` |
| PUT | `/admin/stores/{storeId}/members/{userId}` | `{storeRole}` | create → `201` `STORE_ASSIGNED`; change role → `200` `STORE_ASSIGNMENT_CHANGED`; same role → `200`, no audit |
| DELETE | `/admin/stores/{storeId}/members/{userId}` | | `204`; `STORE_UNASSIGNED`; not assigned → `404` |

Rules: staff → only `STAFF`; client → only `OWNER` or `PARTNER` (`422 INVALID_STORE_ROLE`). Second owner →
`409 OWNER_EXISTS` (the admin must first change or remove the current owner). Assigning to an inactive store or
a disabled user is allowed.

### 8.6 Audit log (`PLATFORM_ADMIN`)

`GET /admin/audit-log?from&to&action&targetType&targetId&limit&offset` → newest first,
`{id, createdAt, actorAdminId, actorName, action, targetType, targetId, targetLabel, detail}`.
Every write in §8.2–8.5 writes its audit row **in the same transaction**. `detail` holds changed field names and
non-secret new values; never passwords, hashes or tokens. A user's own password change and logins are not
audited (`password_changed_at` and `last_login_at` record them).

### 8.7 Error codes

`400 VALIDATION_FAILED` · `401 UNAUTHENTICATED`, `TOKEN_EXPIRED`, `BAD_CREDENTIALS`, `SESSION_ENDED` ·
`403 FORBIDDEN`, `CSRF_REJECTED`, `ACCOUNT_DISABLED` · `404 NOT_FOUND` · `409 EMAIL_TAKEN`, `STORE_CODE_TAKEN`,
`S3_FOLDER_TAKEN`, `OWNER_EXISTS` · `422 WRONG_PASSWORD`, `CANNOT_DISABLE_SELF`, `LAST_ACTIVE_ADMIN`,
`USER_TYPE_FIXED`, `STORE_CODE_FIXED`, `POS_VENDOR_FIXED`, `INVALID_STORE_ROLE`, `INVALID_TIMEZONE`,
`INVALID_<FIELD>` · `423 ACCOUNT_LOCKED` · `500 INTERNAL_ERROR` (no stack trace or SQL in the body).

---

## 9. auth-service configuration

`services/auth-service/src/main/resources/application.properties`:
```properties
spring.application.name=auth-service
spring.config.import=optional:file:${user.home}/.hor-stores/secrets.properties

spring.datasource.url=jdbc:postgresql://${hor.auth.db.host}:${hor.auth.db.port}/auth_db?logServerErrorDetail=false
spring.datasource.username=auth_svc
spring.datasource.password=${hor.auth.db.password}
spring.datasource.hikari.maximum-pool-size=10
spring.flyway.enabled=true
spring.flyway.clean-disabled=true
spring.flyway.validate-on-migrate=true
spring.jackson.default-property-inclusion=non_null

server.port=8091
server.address=127.0.0.1
server.shutdown=graceful
server.forward-headers-strategy=native

hor.auth.db.host=localhost
hor.auth.db.port=5432
hor.auth.signing-key-path=${user.home}/.hor-stores/auth-signing-key.pem
hor.auth.access-token-ttl=PT15M
hor.auth.refresh-token-ttl=PT12H
hor.auth.max-failed-logins=10
hor.auth.lock-duration=PT15M
hor.auth.bcrypt-strength=12
hor.auth.allowed-origins=http://localhost:5180,http://localhost:5181,http://localhost:5182
# empty = host-only cookie (development); production: handsoffretail.com
hor.auth.cookie-domain=

hor.auth.bootstrap.admin.full-name=Platform Administrator
hor.auth.bootstrap.default-store.store-name=Tiki Hut
hor.auth.bootstrap.default-store.store-code=8730_IN-47872
hor.auth.bootstrap.default-store.pos-vendor=VERIFONE
hor.auth.bootstrap.default-store.s3-folder=Tiki_8730_IN-47872/Tiki_8730_IN-47872/
hor.auth.bootstrap.default-store.address-line1=8730 E US Highway 36
hor.auth.bootstrap.default-store.city=Rockville
hor.auth.bootstrap.default-store.state-code=IN
hor.auth.bootstrap.default-store.postal-code=47872
hor.auth.bootstrap.default-store.country-code=US
hor.auth.bootstrap.default-store.timezone=America/Indiana/Indianapolis
```
On the server: `hor.auth.allowed-origins=https://auth.handsoffretail.com,https://apps.handsoffretail.com` and
`hor.auth.cookie-domain=handsoffretail.com`. Changing a host name later touches only these two properties, the
nginx `server_name`s, the TLS certificates and the auth-console build settings (§11.1); no code.

`src/test/resources/application-test.properties`: datasource `auth_db_test`, `spring.flyway.clean-disabled=false`,
`hor.auth.signing-key-path=${java.io.tmpdir}/hor-auth-test-signing-key.pem`, `hor.auth.bcrypt-strength=4`,
`hor.auth.bootstrap.admin.email=bootstrap.admin@test.local`, `hor.auth.bootstrap.admin.password=Bootstrap-Test-Password-1`
(test-only values in the test database; not secrets).

**Secrets file** `%USERPROFILE%\.hor-stores\secrets.properties` (never committed, never printed; create the file
with placeholders if missing and tell the owner what to fill):
```properties
hor.auth.db.password=…
hor.auth.bootstrap.admin.email=…
hor.auth.bootstrap.admin.password=…      # remove after the first start
```
All settings bind to `@ConfigurationProperties` records (`AuthProperties`, `BootstrapProperties`) with
validation (`@Validated`).

---

## 10. auth-service code design

### 10.1 Packages (by feature) under `com.handsoff.hor.auth`

| Package | Contents |
|---|---|
| `session` | `SessionController` (login, refresh, logout, me, me/password), `SessionService`, `LoginAttempts` (lockout), `AuthCookies` (writes/clears both cookies) |
| `token` | `AccessTokenIssuer` (interface) + `JwtAccessTokenIssuer` (Nimbus), `SigningKeyStore` (load / create PEM, `kid`), `JwksController`, `RefreshTokens` (generate, hash) |
| `password` | `PasswordHasher` (interface) + `BCryptPasswordHasher`, `PasswordRules` (12–72 chars, byte length) |
| `account` | `AccountEmails` repository, `Account` (sealed: `AdminAccount`, `StoreUserAccount`) used by login |
| `admins` | controller, `AdminService`, `AdminRepository` + `JdbcAdminRepository`, records |
| `users` | controller, `StoreUserService`, `StoreUserRepository` + JDBC, records |
| `stores` | controller, `StoreService`, `StoreRepository` + JDBC, records, `StoreRules` |
| `assignments` | controller, `AssignmentService`, `AssignmentRepository` + JDBC, `StoreRole` |
| `refresh` | `AdminRefreshTokenRepository`, `UserRefreshTokenRepository` (JDBC), `RefreshTokenCleanupJob` (`@Scheduled`, daily) |
| `audit` | `AuditWriter` (interface) + JDBC, `AuditAction` enum, `AuditController`, `AuditQueryService` |
| `bootstrap` | `FirstStartBootstrap` (`ApplicationRunner`) |
| `config` | `AuthProperties`, `BootstrapProperties`, `SecurityConfig`, `ClockConfig` (`Clock.systemUTC()` bean) |
| `common` | `AuthErrorCode` (enum implementing auth-client's `ErrorCode`, the codes of §8.7), `ConstraintErrors` (constraint name → code), `PageResult` |

### 10.2 Principles (mandatory)
- **SOLID:** single-purpose classes; small role interfaces (`PasswordHasher`, `AccessTokenIssuer`, `AuditWriter`,
  repositories); constructor injection only, against interfaces; time from an injected `java.time.Clock`;
  settings from `@ConfigurationProperties` records.
- **Layers point inward:** web (controllers + request/response records) → application services
  (`@Transactional` **only here**) → domain records and rules (no Spring/JDBC imports) ← infrastructure (JDBC,
  Nimbus, BCrypt adapters). No SQL in services or controllers.
- **Clean code:** immutable records; no nulls returned (use `Optional` / empty lists); Bean Validation at the
  edge; database constraints as the last line; exceptions mapped to ProblemDetail in ONE place (auth-client's
  advice, with auth-service's `ErrorCode`s); never `catch (Exception)`; no magic numbers.
- **YAGNI:** nothing for later phases (no email, no MFA, no staff permissions beyond login).
- Failed-login counter updates must commit even when the login request fails: use a separate
  `REQUIRES_NEW` transaction in `LoginAttempts` or `noRollbackFor` on the domain exception.

### 10.3 Logging
- One line per request: method, path, status, duration ms, account (`ADMIN:7` / `STORE_USER:12` / `-`).
- **Never log** request bodies of `/login`, `/me/password`, `/password-reset`, any cookie, token, hash or
  password. JDBC URL carries `logServerErrorDetail=false` so row values do not appear in exception messages.

---

## 11. auth-console (React 19, JSX, JavaScript)

### 11.1 Setup
- `web/auth-console/`: `package.json` (`react@19.2`, `react-dom@19.2`, `react-router@^7.18`, `vite@^8.3`,
  `@vitejs/plugin-react`, `oxlint@^1.81`); scripts `dev`, `build`, `lint` = `oxlint --deny-warnings`.
- Build settings (`.env.development` / `.env.production`): `VITE_ADMIN_HOME` (dev `http://localhost:5182/admin/`,
  prod `https://apps.handsoffretail.com/admin/`), `VITE_PORTAL_HOME` (dev `http://localhost:5181/portal/`, prod
  `https://apps.handsoffretail.com/portal/`), `VITE_RETURN_ORIGINS` (origins a `return` URL may point to: dev
  `http://localhost:5180,http://localhost:5181,http://localhost:5182`, prod `https://auth.handsoffretail.com,https://apps.handsoffretail.com`).
- `vite.config.js`: `base: '/'`, dev `port: 5180, strictPort: true`, proxy `/api/auth` →
  `http://localhost:8091`.
- Files are `.jsx` / `.js`. Types documented with JSDoc on the API module. No TypeScript.
- Plain CSS with colour tokens on `:root`, a dark-mode variant (`prefers-color-scheme`), works at phone width.

### 11.2 Structure
```
src/
  main.jsx, App.jsx              BrowserRouter (root), routes, RequireAdmin
  api.js                         the ONE module with every fetch call (see 11.3)
  session/SessionProvider.jsx    loads /me, exposes {me, ready, logout}
  hooks/useLoad.js, useAction.js
  ui/ Layout, Table, Field, Modal (native <dialog> + showModal), StatusBadge, ErrorBanner, Pager, format.js
  pages/ LoginPage, AccountPage, AdminsPage, UsersPage, UserDetailPage, StoresPage, StoreDetailPage, AuditPage
```
Pages are containers that load through hooks; presentational components get props only; no business rules in
components (the server decides).

### 11.3 api.js contract (also the pattern other HOR apps copy)
- Every request: `credentials: 'same-origin'`, header `X-HOR-Request: 1`, JSON body where needed.
- On `401` with code `TOKEN_EXPIRED` or `UNAUTHENTICATED`:
  1. acquire the browser lock `navigator.locks.request('hor-refresh', …)` (serializes tabs and apps);
  2. inside the lock, first retry the original request once (another tab may already have refreshed);
  3. if still 401, `POST /api/auth/refresh`; on 204 retry the original request once;
  4. if refresh answers 401, go to `<auth origin>/login?return=<current absolute URL>` (auth-console itself uses
     its own origin; other apps use `https://auth.handsoffretail.com`, dev `http://localhost:5180`).
- Errors are thrown as `ApiError {status, code, message, fieldErrors}` from the problem JSON.
- No token is ever read or stored by JavaScript (cookies are httpOnly); nothing in `localStorage`.

### 11.4 Screens

| Path | Who | What |
|---|---|---|
| `/login?return=` | anyone | email + password; shows lockout / disabled messages. After login: go to `return` if it is an absolute `http(s)` URL whose origin is in `VITE_RETURN_ORIGINS` (or a path on auth-console starting with `/` but not `//`) and the role may use it (store users only under `VITE_PORTAL_HOME` or `/account`); otherwise admins → `VITE_ADMIN_HOME`, store users → `VITE_PORTAL_HOME`. Already logged in → redirect at once. |
| `/account` | any logged-in | name, email, role, stores; change my password (current + new); log out |
| `/admins` | PLATFORM_ADMIN | list, create, edit, disable / enable (not self), reset password, unlock |
| `/users` | PLATFORM_ADMIN | list with filter STAFF / CLIENT / status / search; create (choose type), edit, disable / enable, reset password, unlock |
| `/users/:id` | PLATFORM_ADMIN | user details + their stores with role; assign to a store (role list limited by user type); change role; remove |
| `/stores` | PLATFORM_ADMIN | list (owner, members count, status); create; edit (code and vendor read-only); deactivate / activate |
| `/stores/:id` | PLATFORM_ADMIN | store details + members (owner, partners, staff); add member (search user, pick role); change role; remove; shows "no owner yet" when none |
| `/audit` | PLATFORM_ADMIN | filters date range / action / target; newest first; pager |

Store users opening an admin screen see "You do not have access" with links to the client portal and my account.
Forms: required fields marked, errors shown after interaction (CSS `:user-invalid`, `aria-invalid` kept in step),
server field errors shown next to the form. Navigation shows links to `/admin/` and `/portal/` for platform admins.

---

## 12. auth-client library (for every HOR Spring Boot service)

Package `com.handsoff.hor.auth.client`, Spring Boot auto-configuration
(`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`).

| Piece | Behaviour |
|---|---|
| `AuthCookieTokenResolver` | `BearerTokenResolver` that reads the `hor_access` cookie (ignores `Authorization`) |
| `HorJwtDecoder` config | `NimbusJwtDecoder.withJwkSetUri(hor.auth.client.jwks-uri)`, RS256 only, validators: `iss = hor-auth`, `exp` with 30 s clock skew (injected `Clock`), required claims present. A bean override lets auth-service use its local public key. |
| `HorPrincipal` | record `{accountType, id, role, name, allStores, storeIds (Set<Long>)}` built from the claims |
| `StoreAccess` | `requireStore(principal, storeId)`: allowed if `allStores` or id in `storeIds`; otherwise **404** (never 403, so store ids cannot be probed) |
| `@RequireRole` / `HorHttpSecurity` | helper to declare which roles a path needs (e.g. admin-console APIs: `PLATFORM_ADMIN`; reporting APIs: all three) |
| `CsrfHeaderFilter` | rule §7.4, with `hor.auth.client.allowed-origins` |
| `ProblemResponses` | JSON 401 (`UNAUTHENTICATED` / `TOKEN_EXPIRED`) and 403 (`FORBIDDEN`, `CSRF_REJECTED`) bodies |
| `ErrorCode`, `DomainException` | `ErrorCode` interface (`code()`, `status()`); `DomainException(ErrorCode, detail)` thrown by services of every HOR app |
| `ProblemDetailsAdvice` | maps `DomainException` (code + status) and validation errors to RFC 9457 in one place |
| `RequestLoggingFilter` | the one-line request log of §10.3 |

Properties: `hor.auth.client.jwks-uri` (default `http://127.0.0.1:8091/api/auth/.well-known/jwks.json`: inside the VM),
`hor.auth.client.allowed-origins`. Sessions are stateless (`SessionCreationPolicy.STATELESS`); Spring's own CSRF
is disabled (replaced by `CsrfHeaderFilter`).

---

## 13. Tests (all must pass; real PostgreSQL `auth_db_test`, run one test JVM at a time)

Test support: the test profile cleans and migrates `auth_db_test` per test class (drop schema `core` and the
`citext` objects first, then Flyway clean + migrate), a mutable test `Clock`, an HTTP helper that keeps cookies
and sends `X-HOR-Request: 1`.

**auth-client** (unit + slice tests): cookie resolver; decoder rejects wrong issuer, expired (beyond skew), wrong
key, `alg=none`/HS256; `StoreAccess` 404 rule; CSRF filter (missing header, foreign Origin, allowed Origin, GET
untouched); problem bodies. One contract test for `AccessTokenVerifier`-like interfaces if more than one
implementation exists.

**auth-service** (integration, real HTTP):
1. First start creates exactly one admin and Tiki Hut (no owner) with two audit rows; a second start creates
   nothing; missing bootstrap secrets with an empty table refuses to start.
2. Login sets both cookies with the exact flags/paths of §7.3; `/me` works with the cookie only.
3. Wrong password ×10 → locked (`423`); correct password while locked → `423`; after 15 min (test clock) works;
   unlock works; success resets the counter.
4. Disabled account: correct password → `403 ACCOUNT_DISABLED`; disabling ends existing sessions.
5. Admin single session: login in client A, login in client B → A's refresh `401 SESSION_ENDED`; B works.
6. Store user many sessions: two logins both refresh fine.
7. Refresh rotation; reusing a rotated refresh token revokes the chain; logout clears cookies and refresh fails.
8. CSRF: writes without `X-HOR-Request` → `403`; foreign `Origin` → `403`.
9. Expired access cookie → `401 TOKEN_EXPIRED`; refresh then succeeds.
10. Token claims: admin has `all_stores`; store user has exactly the ACTIVE assigned store ids; deactivating a
    store removes it at the next refresh; JWT verifies against `/.well-known/jwks.json`.
11. Email uniqueness across both tables, case-insensitive (`409 EMAIL_TAKEN`); email change works and stays
    unique.
12. Assignments: staff only STAFF, client only OWNER/PARTNER (`422`); second owner `409 OWNER_EXISTS`; partner
    on a store with no owner works; remove works; disabled user keeps assignments.
13. Stores: code/vendor fixed; s3 folder rule; bad time zone `422`; duplicate code `409`.
14. Admin rules: cannot disable self; cannot disable the last active admin.
15. Every write produces exactly one audit row in the same transaction; audit rows cannot be updated or deleted.
16. Change own password needs the current password and ends the account's other sessions.
17. Store users get `403` on every `/admin/**` endpoint.
18. No password, hash, token or cookie value appears in the captured log output of a full login/refresh/reset run.

**auth-console:** `npm run build` and `npm run lint` clean; a scripted click-through (Playwright with the
installed Chrome is fine) of: login, redirect back to `return`, create staff + client, create store, assign
owner / partner / staff, disable / enable, reset password, audit list, logout.

---

## 14. Build order and definition of done

1. `infra/postgres` scripts; create `auth_db` / `auth_db_test` locally (PostgreSQL 17 on port 5432).
2. Gradle skeleton (root aggregate + two standalone builds + wrappers 9.7.1).
3. auth-client with its tests.
4. auth-service: migration V1 → bootstrap → session/token → admins → users → stores → assignments → audit,
   tests green after each step.
5. auth-console.
6. `docs/RUNNING_LOCALLY.md` (database setup, secrets keys, start order: auth-service then auth-console, ports,
   how to run tests) and templates `infra/nginx/auth.conf` (server `auth.handsoffretail.com`: `location /` → auth-console `dist`
   with SPA fallback, `location /api/auth/` → `proxy_pass http://127.0.0.1:8091`, forward `X-Forwarded-*`; plus the
   same `location /api/auth/` block to include in the `apps.handsoffretail.com` server), `infra/systemd/auth-service.service`
   (Java 21, `-Xmx384m`, `Restart=on-failure`, secrets file mode 600).
7. `docs/BUILD_REPORT.md`: what was built, test counts, any decision taken that this spec did not cover (should
   be none — ask instead), known limitations.

**Done when:** root `gradlew.bat build` passes with all §13 tests; auth-console build + lint pass; the
click-through passes against a running auth-service; the diagram `auth_db.mmd` matches the migration.

---

## 15. Hard rules

- Never print or commit secrets, the signing key, passwords, tokens or cookie values.
- Do not touch the owner's other projects: `C:\Users\abhia\OneDrive\HOR-XML\cpj-platform`, `pjr-platform`,
  databases `cpj_analytics`, `pjr_analytics`, ports 8080 / 8081 / 5173 / 5174.
- No Docker. No SQL newer than PostgreSQL 16.
- Ask the owner before inventing any rule, name, field or behaviour not in this document.

---

## 16. Owner decisions added 2026-10-20 (override earlier sections where they differ)

- **Gradle wrapper 9.8.1** (from Spring Initializr) in all three builds instead of 9.7.1.
- **Database:** PostgreSQL only, locally and in tests. No H2 (its dependencies are removed).
- **List search `q`:** case-insensitive substring. Admins and store users: full name or email. Stores: `store_code` or `store_name`.
- **Audit `targetLabel`:** ADMIN / USER → that account's full name; STORE / ASSIGNMENT → the store's `store_code`.
- **Members `assignedBy`:** the assigning admin's **full name** (not the id).
- **Refresh-token cleanup job:** daily at 03:00 UTC.
- **Server templates:** `User=hor`, jar `/opt/hor-auth/auth-service.jar`, UI `/opt/hor-auth/auth-console/dist`,
  secrets `/home/hor/.hor-stores/` (placeholders, adjust on the VM).
- **auth-client:** `HorHttpSecurity` only; no `@RequireRole` annotation (YAGNI).
- **Admin single session under concurrency:** admin login locks the admin row (`SELECT … FOR UPDATE`) before
  revoking and inserting, so two simultaneous logins can never leave two active sessions. Covered by a test.
- **Signing key file:** owner-only permissions on Windows (NTFS ACL: current user only) and POSIX (600).
- **Login brute force / enumeration:** per-IP rate limit on `/api/auth/login` in the nginx template
  (`limit_req`, 10 requests/min per IP, small burst). No Java rate limiter. Account lockout rules unchanged.
- **Security event log lines** (application log, not `audit_log`): failed login, account locked, refresh-token
  reuse detected, sessions revoked by disable / password reset. Account shown as `ADMIN:7` / `STORE_USER:12` /
  `unknown`; never the email, password, token, hash or cookie.
- **No `aud` claim.** One signing key, no automatic rotation; a manual rotation procedure is documented in
  `RUNNING_LOCALLY.md`.
- **Database role:** `auth_svc` stays owner of `auth_db` (runs Flyway and the app).
- **Access-change delay:** the accepted ≤ 15 min delay (§2) stays.
- **Other subdomains** of `handsoffretail.com` may exist on hosts HOR does not control. Cookie `Domain` stays
  `handsoffretail.com`; the CSRF Origin allowlist is the defence; the cookie-tossing risk is listed in
  `BUILD_REPORT.md` under known limitations.
- **PATCH optional fields:** an empty string removes the optional `phoneNumber` (admin/user), `address` (user),
  `addressLine2`, or `contactNumber` (store).
- **PATCH status:** a request containing `status` and any other field returns `422 STATUS_CHANGE_ALONE`; status
  changes are performed alone so each write creates exactly one audit row. Add this code to `AuthErrorCode`.
- **Locked sessions:** `locked_until > now` blocks password login only; refresh of an existing session remains
  allowed.
- **auth-client publishing:** apply `maven-publish`; `gradlew publishToMavenLocal` publishes
  `com.handsoff.hor:auth-client:0.1.0` for the other repositories using `mavenLocal()`.
- **Audit time filters:** `from` and `to` are ISO-8601 instants; `from` is inclusive and `to` is exclusive.
- **Own admin password reset:** an admin may reset their own password through the admin endpoint; this also ends
  their own sessions.
- **Unlocking an unlocked account:** return `204` and still write the corresponding `*_UNLOCKED` audit action.
- **PATCH nulls:** `null` leaves an optional field unchanged; the approved empty string is the only removal value.
- **PATCH unchanged status:** apply the requested status and write the corresponding status audit action even when it already matches.
- **Pagination bounds:** cap `limit` at 500 and normalize negative `offset` to 0. `limit=0` returns an empty page; a negative limit returns `400`.
