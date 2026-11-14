# Running hor-auth locally

## Prerequisites

- Temurin JDK 21.
- PostgreSQL 17 locally (PostgreSQL 16 is the server target).
- The owner-provided databases `auth_db` and `auth_db_test`, owned by `auth_svc`.
- `%USERPROFILE%\.hor-stores\secrets.properties` with local values for:

```properties
hor.auth.db.password=<local auth_svc password>
hor.auth.bootstrap.admin.email=<first admin email>
hor.auth.bootstrap.admin.password=<first admin password; remove after first start>
```

Do not commit or print this file. The service imports it from the user's home directory. The default first-admin full name and Tiki Hut store settings are in `application.properties`; production may override the cookie domain and allowed origins there.

## Build and test

From the repository root, run the aggregate build. It builds both included standalone projects and runs auth-client tests plus auth-service integration tests against the real PostgreSQL `auth_db_test` database.

```powershell
.\gradlew.bat build
```

The test setup drops and recreates only `auth_db_test`'s `core` schema and `citext` extension. It does not use H2, Docker, or any other database. Never change the test datasource to `auth_db`.

To publish the Java library for other HOR repositories that use `mavenLocal()`:

```powershell
Push-Location libs\auth-client
.\gradlew.bat publishToMavenLocal
Pop-Location
```

## Start the service

The service listens on `127.0.0.1:8091`. Start it after PostgreSQL is available:

```powershell
Push-Location services\auth-service
.\gradlew.bat bootRun
Pop-Location
```

The first successful start creates the platform admin and the default Tiki Hut store in one transaction, writes the two bootstrap audit rows, and logs the one-time reminder to remove `hor.auth.bootstrap.admin.password` from the secrets file. Subsequent starts do not recreate either row. The signing key is generated at `%USERPROFILE%\.hor-stores\auth-signing-key.pem` if it does not exist. Its private material is not logged; Windows ACLs and POSIX permissions are restricted to the owner where supported.

The auth-console is not included in this backend build. Cookies are always `Secure`; local development uses host-only cookies and the service's configured localhost Origin allowlist. No CORS is enabled.

## VM configuration templates

The service systemd unit is in `infra/systemd/auth-service.service`. Install it as `/etc/systemd/system/auth-service.service`, ensure `/home/hor/.hor-stores/secrets.properties` is owned by `hor` and mode `600`, and place the executable jar at `/opt/hor-auth/auth-service.jar`.

`infra/nginx/auth.conf` is a single site file (no snippet): copy it to `/etc/nginx/sites-available/auth.handsoffretail.com`, symlink it into `/etc/nginx/sites-enabled/`, reload nginx, then run `certbot --nginx -d auth.handsoffretail.com` to add HTTPS and the http-to-https redirect. It proxies `/api/auth/` to `127.0.0.1:8091` (login rate limited to 10 requests per minute per IP, burst 5) and serves the console build straight from the clone, `/opt/hor/centralized-auth/hor-auth/web/auth-console/dist` (www-data needs o+x on each parent folder). Another host that needs same-origin `/api/auth/` (e.g. `apps.handsoffretail.com`) copies the two `/api/auth` location blocks, but not the `limit_req_zone` line.

On the VM, set `hor.auth.allowed-origins=https://auth.handsoffretail.com,https://apps.handsoffretail.com` and `hor.auth.cookie-domain=handsoffretail.com`. Keep the service bound to loopback; nginx is the public entry point.

## Manual signing-key rotation

There is one active RSA-2048 signing key and no automatic rotation or dual-key overlap. Schedule a maintenance window because other HOR services cache JWKS for up to one hour.

1. Stop `auth-service` and prevent new login/refresh requests during the rotation.
2. Keep the current key only as a protected backup outside the repository; do not print or copy its contents into logs or tickets.
3. Generate a new PKCS#8 RSA-2048 PEM key in the service key directory, set owner `hor`, and restrict it to owner read/write before replacing the configured key file:

```bash
sudo -u hor sh -c 'umask 077; openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out /home/hor/.hor-stores/auth-signing-key.pem.new'
sudo chown hor:hor /home/hor/.hor-stores/auth-signing-key.pem.new
sudo chmod 600 /home/hor/.hor-stores/auth-signing-key.pem.new
sudo -u hor openssl pkey -in /home/hor/.hor-stores/auth-signing-key.pem.new -check -noout
sudo mv /home/hor/.hor-stores/auth-signing-key.pem.new /home/hor/.hor-stores/auth-signing-key.pem
```

4. Start `auth-service` and verify that the JWKS endpoint advertises the new key. Restart consuming services to refresh their cached JWKS, or wait up to its one-hour cache lifetime before relying on newly issued access tokens.
5. Users can refresh their existing refresh-token sessions to receive access tokens signed by the new key. Previously issued access tokens use the old key and are no longer verifiable after consumers refresh JWKS; they expire within 15 minutes.
6. Securely remove the old-key backup when the maintenance/rollback window is over.
