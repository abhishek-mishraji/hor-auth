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

CREATE FUNCTION core.audit_log_append_only() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'core.audit_log is append-only';
END $$;
CREATE TRIGGER tr_audit_log_append_only BEFORE UPDATE OR DELETE ON core.audit_log
    FOR EACH ROW EXECUTE FUNCTION core.audit_log_append_only();
