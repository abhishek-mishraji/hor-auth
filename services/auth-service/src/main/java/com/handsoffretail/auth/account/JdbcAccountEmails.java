package com.handsoffretail.auth.account;

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
public class JdbcAccountEmails implements AccountEmails {
    private final JdbcClient jdbcClient;

    public JdbcAccountEmails(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Optional<Account> findByEmail(String email) {
        Optional<String> kind = jdbcClient.sql("SELECT account_kind FROM core.account_emails WHERE email = :email")
                .param("email", email)
                .query(String.class)
                .optional();
        if (kind.isEmpty()) {
            return Optional.empty();
        }
        return kind.get().equals("ADMIN") ? findAdminByEmail(email) : findUserByEmail(email);
    }

    @Override
    public Optional<Account> findById(AccountType accountType, long id) {
        return accountType == AccountType.ADMIN ? findAdminById(id) : findUserById(id);
    }

    @Override
    public void lockAdminRow(long adminId) {
        jdbcClient.sql("SELECT admin_id FROM core.platform_admins WHERE admin_id = :adminId FOR UPDATE")
                .param("adminId", adminId)
                .query(Long.class)
                .single();
    }

    @Override
    public void updatePassword(AccountType accountType, long id, String passwordHash, Instant changedAt) {
        String table = accountType == AccountType.ADMIN ? "core.platform_admins" : "core.store_users";
        String idColumn = accountType == AccountType.ADMIN ? "admin_id" : "user_id";
        jdbcClient.sql("""
                UPDATE %s SET password_hash = :passwordHash, password_changed_at = :changedAt,
                              updated_at = :changedAt
                WHERE %s = :id
                """.formatted(table, idColumn))
                .param("passwordHash", passwordHash)
                .param("changedAt", changedAt.atOffset(ZoneOffset.UTC))
                .param("id", id)
                .update();
    }

    private Optional<Account> findAdminByEmail(String email) {
        return jdbcClient.sql("""
                SELECT admin_id, email, full_name, phone_number, password_hash, status, failed_login_count,
                       locked_until, password_changed_at, last_login_at
                FROM core.platform_admins WHERE email = :email
                """)
                .param("email", email)
                .query((rs, rowNum) -> mapAdmin(rs))
                .optional()
                .map(account -> account);
    }

    private Optional<Account> findAdminById(long id) {
        return jdbcClient.sql("""
                SELECT admin_id, email, full_name, phone_number, password_hash, status, failed_login_count,
                       locked_until, password_changed_at, last_login_at
                FROM core.platform_admins WHERE admin_id = :id
                """)
                .param("id", id)
                .query((rs, rowNum) -> mapAdmin(rs))
                .optional()
                .map(account -> account);
    }

    private Optional<Account> findUserByEmail(String email) {
        return jdbcClient.sql("""
                SELECT user_id, user_type, email, full_name, phone_number, address, password_hash, status,
                       failed_login_count, locked_until, password_changed_at, last_login_at
                FROM core.store_users WHERE email = :email
                """)
                .param("email", email)
                .query((rs, rowNum) -> mapUser(rs))
                .optional()
                .map(account -> (Account) account);
    }

    private Optional<Account> findUserById(long id) {
        return jdbcClient.sql("""
                SELECT user_id, user_type, email, full_name, phone_number, address, password_hash, status,
                       failed_login_count, locked_until, password_changed_at, last_login_at
                FROM core.store_users WHERE user_id = :id
                """)
                .param("id", id)
                .query((rs, rowNum) -> mapUser(rs))
                .optional()
                .map(account -> (Account) account);
    }

    private AdminAccount mapAdmin(ResultSet rs) throws SQLException {
        return new AdminAccount(rs.getLong("admin_id"), rs.getString("email"), rs.getString("full_name"),
                rs.getString("phone_number"), rs.getString("password_hash"), rs.getString("status"),
                rs.getInt("failed_login_count"), instant(rs, "locked_until"),
                instant(rs, "password_changed_at"), instant(rs, "last_login_at"));
    }

    private StoreUserAccount mapUser(ResultSet rs) throws SQLException {
        long id = rs.getLong("user_id");
        List<StoreMembership> stores = jdbcClient.sql("""
                SELECT s.store_id, s.store_code, s.store_name, sus.store_role
                FROM core.store_user_stores sus
                JOIN core.stores s ON s.store_id = sus.store_id
                WHERE sus.user_id = :userId AND s.status = 'ACTIVE'
                ORDER BY s.store_id
                """)
                .param("userId", id)
                .query((storeRs, rowNum) -> new StoreMembership(storeRs.getLong("store_id"),
                        storeRs.getString("store_code"), storeRs.getString("store_name"),
                        storeRs.getString("store_role")))
                .list();
        return new StoreUserAccount(id, rs.getString("user_type"), rs.getString("email"),
                rs.getString("full_name"), rs.getString("phone_number"), rs.getString("address"),
                rs.getString("password_hash"), rs.getString("status"), rs.getInt("failed_login_count"),
                instant(rs, "locked_until"), instant(rs, "password_changed_at"), instant(rs, "last_login_at"), stores);
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
