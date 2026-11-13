package com.handsoffretail.auth.admins;

import com.handsoffretail.auth.common.PageResult;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAdminRepository implements AdminRepository {
    private final JdbcClient jdbcClient;

    public JdbcAdminRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public PageResult<PlatformAdminView> findAll(String status, String query, int limit, int offset, Instant now) {
        String where = "WHERE (CAST(:status AS text) IS NULL OR status = :status) "
                + "AND (CAST(:query AS text) IS NULL OR strpos(lower(full_name), lower(:query)) > 0 "
                + "OR strpos(lower(email::text), lower(:query)) > 0)";
        long total = jdbcClient.sql("SELECT count(*) FROM core.platform_admins " + where)
                .param("status", status)
                .param("query", query)
                .query(Long.class)
                .single();
        List<PlatformAdminView> items = jdbcClient.sql("""
                SELECT admin_id, email, full_name, phone_number, status, locked_until,
                       failed_login_count, password_changed_at, last_login_at, created_at
                FROM core.platform_admins
                """ + where + " ORDER BY admin_id LIMIT :limit OFFSET :offset")
                .param("status", status)
                .param("query", query)
                .param("limit", limit)
                .param("offset", offset)
                .query((rs, rowNum) -> map(rs))
                .list();
        return new PageResult<>(items, total, limit, offset);
    }

    @Override
    public Optional<PlatformAdminView> findById(long id, Instant now) {
        return jdbcClient.sql("""
                SELECT admin_id, email, full_name, phone_number, status, locked_until,
                       failed_login_count, password_changed_at, last_login_at, created_at
                FROM core.platform_admins WHERE admin_id = :id
                """)
                .param("id", id)
                .query((rs, rowNum) -> map(rs))
                .optional();
    }

    @Override
    public long create(String email, String fullName, String phoneNumber, String passwordHash, Instant now) {
        jdbcClient.sql("""
                INSERT INTO core.account_emails (email, account_kind, created_at)
                VALUES (:email, 'ADMIN', :now)
                """)
                .param("email", email)
                .param("now", now.atOffset(ZoneOffset.UTC))
                .update();
        return jdbcClient.sql("""
                INSERT INTO core.platform_admins
                    (email, full_name, phone_number, password_hash, password_changed_at, created_at, updated_at)
                VALUES (:email, :fullName, :phoneNumber, :passwordHash, :now, :now, :now)
                RETURNING admin_id
                """)
                .param("email", email)
                .param("fullName", fullName)
                .param("phoneNumber", phoneNumber)
                .param("passwordHash", passwordHash)
                .param("now", now.atOffset(ZoneOffset.UTC))
                .query(Long.class)
                .single();
    }

    @Override
    public void changeEmail(String currentEmail, String newEmail) {
        jdbcClient.sql("UPDATE core.account_emails SET email = :newEmail WHERE email = :currentEmail")
                .param("newEmail", newEmail)
                .param("currentEmail", currentEmail)
                .update();
    }

    @Override
    public void changeFullName(long id, String fullName, Instant now) {
        jdbcClient.sql("UPDATE core.platform_admins SET full_name = :fullName, updated_at = :now WHERE admin_id = :id")
                .param("fullName", fullName)
                .param("now", now.atOffset(ZoneOffset.UTC))
                .param("id", id)
                .update();
    }

    @Override
    public void changePhoneNumber(long id, String phoneNumber, Instant now) {
        jdbcClient.sql("UPDATE core.platform_admins SET phone_number = :phoneNumber, updated_at = :now WHERE admin_id = :id")
                .param("phoneNumber", phoneNumber)
                .param("now", now.atOffset(ZoneOffset.UTC))
                .param("id", id)
                .update();
    }

    @Override
    public void changeStatus(long id, String status, Instant now) {
        jdbcClient.sql("UPDATE core.platform_admins SET status = :status, updated_at = :now WHERE admin_id = :id")
                .param("status", status)
                .param("now", now.atOffset(ZoneOffset.UTC))
                .param("id", id)
                .update();
    }

    @Override
    public void resetPassword(long id, String passwordHash, Instant now) {
        jdbcClient.sql("""
                UPDATE core.platform_admins SET password_hash = :passwordHash, password_changed_at = :now,
                                                updated_at = :now WHERE admin_id = :id
                """)
                .param("passwordHash", passwordHash)
                .param("now", now.atOffset(ZoneOffset.UTC))
                .param("id", id)
                .update();
    }

    @Override
    public void unlock(long id, Instant now) {
        jdbcClient.sql("""
                UPDATE core.platform_admins SET failed_login_count = 0, locked_until = NULL,
                                                updated_at = :now WHERE admin_id = :id
                """)
                .param("now", now.atOffset(ZoneOffset.UTC))
                .param("id", id)
                .update();
    }

    @Override
    public long countActive() {
        return jdbcClient.sql("SELECT count(*) FROM core.platform_admins WHERE status = 'ACTIVE'")
                .query(Long.class)
                .single();
    }

    @Override
    public void lockActiveRows() {
        jdbcClient.sql("SELECT admin_id FROM core.platform_admins WHERE status = 'ACTIVE' ORDER BY admin_id FOR UPDATE")
                .query(Long.class)
                .list();
    }

    private PlatformAdminView map(ResultSet rs) throws SQLException {
        return new PlatformAdminView(rs.getLong("admin_id"), rs.getString("email"), rs.getString("full_name"),
                rs.getString("phone_number"), rs.getString("status"), instant(rs, "locked_until"),
                rs.getInt("failed_login_count"), instant(rs, "password_changed_at"),
                instant(rs, "last_login_at"), instant(rs, "created_at"));
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
