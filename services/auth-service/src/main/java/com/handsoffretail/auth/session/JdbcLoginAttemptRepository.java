package com.handsoffretail.auth.session;

import com.handsoffretail.auth.account.AccountType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcLoginAttemptRepository implements LoginAttemptRepository {
    private final JdbcClient jdbcClient;

    public JdbcLoginAttemptRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public LoginFailureResult recordFailure(AccountType accountType, long accountId, int maxFailedLogins,
            Instant lockedUntilCandidate) {
        String table = accountType == AccountType.ADMIN ? "core.platform_admins" : "core.store_users";
        String idColumn = accountType == AccountType.ADMIN ? "admin_id" : "user_id";
        return jdbcClient.sql("""
                UPDATE %s
                SET failed_login_count = failed_login_count + 1,
                    locked_until = CASE WHEN failed_login_count + 1 >= :maxFailedLogins
                                        THEN :lockedUntil ELSE locked_until END
                WHERE %s = :accountId
                RETURNING failed_login_count, locked_until
                """.formatted(table, idColumn))
                .param("maxFailedLogins", maxFailedLogins)
                .param("lockedUntil", lockedUntilCandidate.atOffset(ZoneOffset.UTC))
                .param("accountId", accountId)
                .query((rs, rowNum) -> new LoginFailureResult(rs.getInt("failed_login_count"),
                        instant(rs, "locked_until")))
                .single();
    }

    @Override
    public void recordSuccess(AccountType accountType, long accountId, Instant now) {
        String table = accountType == AccountType.ADMIN ? "core.platform_admins" : "core.store_users";
        String idColumn = accountType == AccountType.ADMIN ? "admin_id" : "user_id";
        jdbcClient.sql("""
                UPDATE %s SET failed_login_count = 0, locked_until = NULL,
                              last_login_at = :now, updated_at = :now
                WHERE %s = :accountId
                """.formatted(table, idColumn))
                .param("now", now.atOffset(ZoneOffset.UTC))
                .param("accountId", accountId)
                .update();
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        java.time.OffsetDateTime value = rs.getObject(column, java.time.OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
