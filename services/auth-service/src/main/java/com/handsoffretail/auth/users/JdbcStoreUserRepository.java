package com.handsoffretail.auth.users;

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
public class JdbcStoreUserRepository implements StoreUserRepository {
    private final JdbcClient jdbcClient;

    public JdbcStoreUserRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public PageResult<StoreUserView> findAll(String userType, String status, String query, int limit, int offset) {
        String where = "WHERE (CAST(:userType AS text) IS NULL OR su.user_type = :userType) "
                + "AND (CAST(:status AS text) IS NULL OR su.status = :status) "
                + "AND (CAST(:query AS text) IS NULL OR strpos(lower(su.full_name), lower(:query)) > 0 "
                + "OR strpos(lower(su.email::text), lower(:query)) > 0)";
        String from = " FROM core.store_users su "
                + "LEFT JOIN LATERAL (SELECT count(*) AS store_count FROM core.store_user_stores sus "
                + "WHERE sus.user_id = su.user_id) sc ON true ";
        long total = jdbcClient.sql("SELECT count(*)" + from + where)
                .param("userType", userType)
                .param("status", status)
                .param("query", query)
                .query(Long.class)
                .single();
        List<StoreUserView> items = jdbcClient.sql("""
                SELECT su.user_id, su.user_type, su.email, su.full_name, su.phone_number, su.address, su.status,
                       su.locked_until, su.failed_login_count, su.password_changed_at, su.last_login_at,
                       su.created_at, sc.store_count
                """ + from + where + " ORDER BY su.user_id LIMIT :limit OFFSET :offset")
                .param("userType", userType)
                .param("status", status)
                .param("query", query)
                .param("limit", limit)
                .param("offset", offset)
                .query((rs, rowNum) -> map(rs))
                .list();
        return new PageResult<>(items, total, limit, offset);
    }

    @Override
    public Optional<StoreUserView> findById(long id) {
        return jdbcClient.sql("""
                SELECT su.user_id, su.user_type, su.email, su.full_name, su.phone_number, su.address, su.status,
                       su.locked_until, su.failed_login_count, su.password_changed_at, su.last_login_at,
                       su.created_at,
                       (SELECT count(*) FROM core.store_user_stores sus WHERE sus.user_id = su.user_id) AS store_count
                FROM core.store_users su WHERE su.user_id = :id
                """)
                .param("id", id)
                .query((rs, rowNum) -> map(rs))
                .optional();
    }

    @Override
    public List<UserStoreView> findStores(long userId) {
        return jdbcClient.sql("""
                SELECT s.store_id, s.store_code, s.store_name, sus.store_role, s.status AS store_status
                FROM core.store_user_stores sus
                JOIN core.stores s ON s.store_id = sus.store_id
                WHERE sus.user_id = :userId
                ORDER BY s.store_id
                """)
                .param("userId", userId)
                .query((rs, rowNum) -> new UserStoreView(rs.getLong("store_id"), rs.getString("store_code"),
                        rs.getString("store_name"), rs.getString("store_role"), rs.getString("store_status")))
                .list();
    }

    @Override
    public long create(String userType, String email, String fullName, String phoneNumber, String address,
            String passwordHash, long actorAdminId, Instant now) {
        jdbcClient.sql("""
                INSERT INTO core.account_emails (email, account_kind, created_at)
                VALUES (:email, 'STORE_USER', :now)
                """)
                .param("email", email)
                .param("now", now.atOffset(ZoneOffset.UTC))
                .update();
        return jdbcClient.sql("""
                INSERT INTO core.store_users
                    (user_type, full_name, email, phone_number, address, password_hash,
                     password_changed_at, created_by, created_at, updated_at)
                VALUES (:userType, :fullName, :email, :phoneNumber, :address, :passwordHash,
                        :now, :actorAdminId, :now, :now)
                RETURNING user_id
                """)
                .param("userType", userType)
                .param("fullName", fullName)
                .param("email", email)
                .param("phoneNumber", phoneNumber)
                .param("address", address)
                .param("passwordHash", passwordHash)
                .param("now", now.atOffset(ZoneOffset.UTC))
                .param("actorAdminId", actorAdminId)
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
        jdbcClient.sql("UPDATE core.store_users SET full_name = :fullName, updated_at = :now WHERE user_id = :id")
                .param("fullName", fullName)
                .param("now", now.atOffset(ZoneOffset.UTC))
                .param("id", id)
                .update();
    }

    @Override
    public void changePhoneNumber(long id, String phoneNumber, Instant now) {
        jdbcClient.sql("UPDATE core.store_users SET phone_number = :phoneNumber, updated_at = :now WHERE user_id = :id")
                .param("phoneNumber", phoneNumber)
                .param("now", now.atOffset(ZoneOffset.UTC))
                .param("id", id)
                .update();
    }

    @Override
    public void changeAddress(long id, String address, Instant now) {
        jdbcClient.sql("UPDATE core.store_users SET address = :address, updated_at = :now WHERE user_id = :id")
                .param("address", address)
                .param("now", now.atOffset(ZoneOffset.UTC))
                .param("id", id)
                .update();
    }

    @Override
    public void changeStatus(long id, String status, Instant now) {
        jdbcClient.sql("UPDATE core.store_users SET status = :status, updated_at = :now WHERE user_id = :id")
                .param("status", status)
                .param("now", now.atOffset(ZoneOffset.UTC))
                .param("id", id)
                .update();
    }

    @Override
    public void resetPassword(long id, String passwordHash, Instant now) {
        jdbcClient.sql("""
                UPDATE core.store_users SET password_hash = :passwordHash, password_changed_at = :now,
                                            updated_at = :now WHERE user_id = :id
                """)
                .param("passwordHash", passwordHash)
                .param("now", now.atOffset(ZoneOffset.UTC))
                .param("id", id)
                .update();
    }

    @Override
    public void unlock(long id, Instant now) {
        jdbcClient.sql("""
                UPDATE core.store_users SET failed_login_count = 0, locked_until = NULL,
                                            updated_at = :now WHERE user_id = :id
                """)
                .param("now", now.atOffset(ZoneOffset.UTC))
                .param("id", id)
                .update();
    }

    private StoreUserView map(ResultSet rs) throws SQLException {
        return new StoreUserView(rs.getLong("user_id"), rs.getString("user_type"), rs.getString("email"),
                rs.getString("full_name"), rs.getString("phone_number"), rs.getString("address"),
                rs.getString("status"), instant(rs, "locked_until"), rs.getInt("failed_login_count"),
                instant(rs, "password_changed_at"), instant(rs, "last_login_at"), instant(rs, "created_at"),
                rs.getLong("store_count"));
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
