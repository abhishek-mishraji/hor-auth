package com.handsoffretail.auth.bootstrap;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcBootstrapRepository implements BootstrapRepository {
    private final JdbcClient jdbcClient;

    public JdbcBootstrapRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public boolean hasAdmins() {
        return jdbcClient.sql("SELECT EXISTS (SELECT 1 FROM core.platform_admins)")
                .query(Boolean.class)
                .single();
    }

    @Override
    public Optional<Long> firstAdminId() {
        return jdbcClient.sql("SELECT admin_id FROM core.platform_admins ORDER BY admin_id LIMIT 1")
                .query(Long.class)
                .optional();
    }

    @Override
    public boolean hasStores() {
        return jdbcClient.sql("SELECT EXISTS (SELECT 1 FROM core.stores)")
                .query(Boolean.class)
                .single();
    }

    @Override
    public long createAdmin(String fullName, String email, String passwordHash, Instant now) {
        jdbcClient.sql("INSERT INTO core.account_emails (email, account_kind, created_at) VALUES (:email, 'ADMIN', :now)")
                .param("email", email)
                        .param("now", now.atOffset(ZoneOffset.UTC))
                .update();
        return jdbcClient.sql("""
                INSERT INTO core.platform_admins
                    (full_name, email, password_hash, password_changed_at, created_at, updated_at)
                VALUES (:fullName, :email, :passwordHash, :now, :now, :now)
                RETURNING admin_id
                """)
                .param("fullName", fullName)
                .param("email", email)
                .param("passwordHash", passwordHash)
                                .param("now", now.atOffset(ZoneOffset.UTC))
                .query(Long.class)
                .single();
    }

    @Override
    public long createStore(BootstrapStore store, Instant now) {
        return jdbcClient.sql("""
                INSERT INTO core.stores
                    (store_code, store_name, pos_vendor, s3_folder, address_line1, city, state_code,
                     postal_code, country_code, timezone, created_at, updated_at)
                VALUES (:storeCode, :storeName, :posVendor, :s3Folder, :addressLine1, :city, :stateCode,
                        :postalCode, :countryCode, :timezone, :now, :now)
                RETURNING store_id
                """)
                .param("storeCode", store.storeCode())
                .param("storeName", store.storeName())
                .param("posVendor", store.posVendor())
                .param("s3Folder", store.s3Folder())
                .param("addressLine1", store.addressLine1())
                .param("city", store.city())
                .param("stateCode", store.stateCode())
                .param("postalCode", store.postalCode())
                .param("countryCode", store.countryCode())
                .param("timezone", store.timezone())
                                .param("now", now.atOffset(ZoneOffset.UTC))
                .query(Long.class)
                .single();
    }
}
